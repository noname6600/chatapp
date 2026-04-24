import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react"
import type { ReactNode } from "react"

import {
  getNotificationsApi,
  clearRoomNotificationsApi,
  getRoomMuteSettingsApi,
  markAllNotificationsReadApi,
  markNotificationReadApi,
  updateRoomNotificationModeApi,
} from "../api/notification.service"

import { useAuth } from "./auth.store"
import { isFeatureEnabled } from "../config/featureFlags"
import { NotificationEventType } from "../constants/notificationEvents"
import {
  connectNotificationSocket,
  disconnectNotificationSocket,
  onNotificationEvent,
  onNotificationSocketOpen,
} from "../websocket/notification.socket"
import {
  normalizeRoomNotificationMode,
  shouldCountNotificationAsUnreadByMode,
} from "../utils/notificationModePolicy"

import type {
  Notification,
  RoomNotificationMode,
  UnreadCountResponse,
} from "../types/notification"

interface NotificationContextType {
  notifications: Notification[]
  unreadCount: number
  hasMoreNotifications: boolean
  isLoadingMoreNotifications: boolean
  notificationModesByRoom: Record<string, RoomNotificationMode>
  fetchNotifications: () => Promise<void>
  loadMoreNotifications: () => Promise<void>
  markRead: (id: string) => Promise<void>
  markAllRead: () => Promise<void>
  clearRoomNotifications: (roomId: string) => Promise<void>
  fetchRoomNotificationMode: (roomId: string) => Promise<RoomNotificationMode>
  setRoomNotificationMode: (roomId: string, mode: RoomNotificationMode) => Promise<void>
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined)
const ROOM_NOTIFICATION_MODE_STORAGE_KEY = "notification_modes_by_room"
const LEGACY_ROOM_MUTE_STORAGE_KEY = "notification_mutes_by_room"

type SyncMode = "replace" | "reconcile"
type SyncTriggerReason = "initial_load" | "socket_reconnect" | "manual_action" | "post-mark-read"

const RECONNECT_SYNC_COOLDOWN_MS = 2000
const NOTIFICATION_PAGE_SIZE = 50

// Pending read scopes track optimistic read operations until server confirmation.
// The operation covers all notifications currently in state at the time the action
// was triggered, so no timestamp comparison is needed — any notification that arrived
// strictly after the scope was registered will not yet be in state and will not have
// been optimistically marked read, so the server snapshot is the source of truth for those.
interface PendingMarkAllReadScope {
  type: "all"
}
interface PendingRoomClearScope {
  type: "room"
  roomId: string
}
type PendingReadScope = PendingMarkAllReadScope | PendingRoomClearScope

const normalizeNotificationReadFlag = <T extends Notification>(notification: T): T => {
  const candidate = notification as T & { read?: boolean }
  const isRead =
    typeof candidate.isRead === "boolean"
      ? candidate.isRead
      : typeof candidate.read === "boolean"
        ? candidate.read
        : true

  return {
    ...notification,
    isRead,
  }
}

const isCoveredByPendingScope = (
  notification: Notification,
  pendingScopes: PendingReadScope[]
): boolean => {
  return pendingScopes.some((scope) => {
    if (scope.type === "all") {
      return notification.type !== "FRIEND_REQUEST"
    }
    return scope.type === "room" && notification.roomId === scope.roomId && notification.type !== "FRIEND_REQUEST"
  })
}

const isValidRealtimeNotification = (notification: Notification): boolean => {
  if (notification.type !== "MENTION") {
    return true
  }

  return Boolean(notification.referenceId && notification.roomId)
}

const isUnresolvedFriendRequest = (notification: Notification) => {
  if (notification.type !== "FRIEND_REQUEST") {
    return false
  }

  if (notification.isRead) {
    return false
  }

  // Backward compatibility: if actionRequired is missing, treat unread requests as unresolved.
  return notification.actionRequired !== false
}

const shouldIncrementUnread = (
  notification: Notification,
  modesByRoom: Record<string, RoomNotificationMode>,
  currentUserId: string | null
) => {
  if (notification.isRead) return false

  if (notification.type === "FRIEND_REQUEST") {
    return isUnresolvedFriendRequest(notification)
  }

  if (
    isFeatureEnabled("enableSelfMessageUnreadExclusion") &&
    currentUserId != null &&
    notification.senderId === currentUserId
  ) {
    return false
  }

  const isRoomNotification =
    notification.type === "MESSAGE" || notification.type === "MENTION" || notification.type === "REPLY" || notification.type === "REACTION" || notification.type === "GROUP_INVITE"

  if (!isRoomNotification || !notification.roomId) {
    return true
  }

  return shouldCountNotificationAsUnreadByMode(notification, modesByRoom)
}

const isPinnedNotification = (notification: Notification) => {
  if (notification.actionRequired && !notification.isRead) {
    return true
  }

  // Backward-compatible pinning rule if BE hasn't started sending actionRequired yet.
  return notification.type === "FRIEND_REQUEST" && !notification.isRead
}

const toTimestamp = (createdAt: string | null | undefined) => {
  if (!createdAt) return 0

  const ts = Date.parse(createdAt)
  return Number.isNaN(ts) ? 0 : ts
}

const mergeNotifications = (
  primary: Notification[],
  secondary: Notification[] = []
): Notification[] => {
  const map = new Map<string, Notification>()

  for (const item of secondary) {
    map.set(item.id, item)
  }

  for (const item of primary) {
    const existing = map.get(item.id)
    if (!existing) {
      map.set(item.id, item)
      continue
    }

    map.set(
      item.id,
      toTimestamp(item.createdAt) >= toTimestamp(existing.createdAt) ? item : existing
    )
  }

  return [...map.values()].sort((a, b) => {
    const pinnedA = isPinnedNotification(a) ? 1 : 0
    const pinnedB = isPinnedNotification(b) ? 1 : 0
    if (pinnedA !== pinnedB) return pinnedB - pinnedA
    const tsDiff = toTimestamp(b.createdAt) - toTimestamp(a.createdAt)
    if (tsDiff !== 0) return tsDiff
    // Secondary tiebreak: id DESC (lexicographic, mirrors backend `id DESC` ordering)
    return b.id > a.id ? 1 : b.id < a.id ? -1 : 0
  })
}

const getNewestTimestamp = (items: Notification[]) => {
  if (items.length === 0) return 0
  return items.reduce((maxTs, item) => Math.max(maxTs, toTimestamp(item.createdAt)), 0)
}

const pruneStaleFriendRequests = (
  merged: Notification[],
  authoritativeIncoming: Notification[],
  serverNewestTimestamp: number,
  realtimeTimestampFloor: number = 0
) => {
  const incomingIds = new Set(authoritativeIncoming.map((item) => item.id))
  // A friend request must be newer than both the server snapshot window and the
  // latest realtime event we have seen to be considered a genuinely unobserved
  // arrival. Using only serverNewestTimestamp is insufficient when the server
  // snapshot contains fewer items (e.g. reconnect returns a small page), because
  // stale friend requests with timestamps just after serverNewestTimestamp would
  // survive incorrectly.
  const keepThreshold = Math.max(serverNewestTimestamp, realtimeTimestampFloor)

  return merged.filter((item) => {
    if (item.type !== "FRIEND_REQUEST") {
      return true
    }

    if (incomingIds.has(item.id)) {
      return true
    }

    // Keep genuinely newer realtime friend requests that neither the server
    // snapshot nor the latest realtime window has observed yet.
    return toTimestamp(item.createdAt) > keepThreshold
  })
}

const countUnreadNotifications = (
  items: Notification[],
  modesByRoom: Record<string, RoomNotificationMode>,
  currentUserId: string | null
) => {
  return items.reduce((count, item) => {
    if (!shouldIncrementUnread(item, modesByRoom, currentUserId)) {
      return count
    }

    return count + 1
  }, 0)
}

const hasRoomModeOverrides = (modesByRoom: Record<string, RoomNotificationMode>) => {
  return Object.values(modesByRoom).some((mode) => mode !== "NO_RESTRICT")
}

const parseStoredModes = (): Record<string, RoomNotificationMode> => {
  const parsedModes: Record<string, RoomNotificationMode> = {}

  try {
    const raw = localStorage.getItem(ROOM_NOTIFICATION_MODE_STORAGE_KEY)
    if (raw) {
      const stored = JSON.parse(raw) as Record<string, unknown>
      for (const [roomId, value] of Object.entries(stored)) {
        if (value === "NO_RESTRICT" || value === "ONLY_MENTION" || value === "NOTHING") {
          parsedModes[roomId] = value
        }
      }
    }
  } catch {
    // Ignore malformed storage and continue.
  }

  // Compatibility path for old boolean mute storage.
  try {
    const legacyRaw = localStorage.getItem(LEGACY_ROOM_MUTE_STORAGE_KEY)
    if (legacyRaw) {
      const legacy = JSON.parse(legacyRaw) as Record<string, boolean>
      for (const [roomId, muted] of Object.entries(legacy)) {
        if (parsedModes[roomId] != null) continue
        parsedModes[roomId] = muted ? "NOTHING" : "NO_RESTRICT"
      }
    }
  } catch {
    // Ignore malformed legacy data.
  }

  return parsedModes
}

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { accessToken, userId } = useAuth()

  const [notifications, setNotifications] = useState<Notification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [notificationModesByRoom, setNotificationModesByRoom] = useState<Record<string, RoomNotificationMode>>(
    () => parseStoredModes()
  )

  const notificationModesByRoomRef = useRef<Record<string, RoomNotificationMode>>({})
  const notificationsRef = useRef<Notification[]>([])
  notificationModesByRoomRef.current = notificationModesByRoom
  notificationsRef.current = notifications
  const latestRealtimeTimestampRef = useRef(0)
  const realtimeNotificationIdsRef = useRef<Set<string>>(new Set())
  const pendingScopesRef = useRef<PendingReadScope[]>([])

  const syncInFlightRef = useRef<Promise<void> | null>(null)
  const lastSyncTimeRef = useRef(0)
  const lastSyncTriggerRef = useRef<SyncTriggerReason | null>(null)
  const hasMoreNotificationsRef = useRef(false)
  const nextPageRef = useRef<number | null>(null)
  const windowCreatedAtRef = useRef<string | null>(null)
  const loadingMoreRef = useRef(false)

  const [hasMoreNotifications, setHasMoreNotifications] = useState(false)
  const [isLoadingMoreNotifications, setIsLoadingMoreNotifications] = useState(false)

  const setNotificationsState = useCallback(
    (updater: Notification[] | ((prev: Notification[]) => Notification[])) => {
      // Eagerly update the ref when given a plain array so that any event handler
      // that runs between the setState call and React's batch commit sees fresh data.
      // The ref is also updated inside the setState callback as a double-safety measure.
      if (typeof updater !== "function") {
        notificationsRef.current = updater
      }
      setNotifications((prev) => {
        const next = typeof updater === "function"
          ? (updater as (prev: Notification[]) => Notification[])(prev)
          : updater
        notificationsRef.current = next
        return next
      })
    },
    []
  )

  const applyRealtimeNotification = useCallback((payload: Notification) => {
    const normalizedPayload = normalizeNotificationReadFlag(payload)
    const isValid = isValidRealtimeNotification(normalizedPayload)

    console.log("[notification-store] Applying realtime notification", {
      id: normalizedPayload.id,
      type: normalizedPayload.type,
      isRead: normalizedPayload.isRead,
      roomId: normalizedPayload.roomId,
      referenceId: normalizedPayload.referenceId,
      senderName: normalizedPayload.senderName,
      preview: normalizedPayload.preview,
      isValid,
    })

    if (!isValid) {
      console.warn("[notification-store] Validation failed — MENTION requires referenceId + roomId", normalizedPayload)
      return
    }

    latestRealtimeTimestampRef.current = Math.max(
      latestRealtimeTimestampRef.current,
      toTimestamp(normalizedPayload.createdAt)
    )
    realtimeNotificationIdsRef.current.add(normalizedPayload.id)

    const isNew = !notificationsRef.current.some((item) => item.id === normalizedPayload.id)

    const mergedNotifications = mergeNotifications([normalizedPayload], notificationsRef.current)
    setNotificationsState(mergedNotifications)

    const unreadFloor = countUnreadNotifications(
      mergedNotifications,
      notificationModesByRoomRef.current,
      userId ?? null
    )

    setUnreadCount((prev) => {
      const next =
        isNew && shouldIncrementUnread(normalizedPayload, notificationModesByRoomRef.current, userId ?? null)
          ? prev + 1
          : prev

      console.log("[notification-store] Unread count update", {
        prev,
        next: Math.max(unreadFloor, next),
        unreadFloor,
        isNew,
        shouldIncrement: shouldIncrementUnread(normalizedPayload, notificationModesByRoomRef.current, userId ?? null),
      })

      return Math.max(unreadFloor, next)
    })
  }, [setNotificationsState, userId])

  const syncNotifications = useCallback(
    async (
      mode: SyncMode = "replace",
      triggerReason: SyncTriggerReason = "manual_action"
    ) => {
      const now = Date.now()
      const timeSinceLastSync = now - lastSyncTimeRef.current
      const isReconnectTrigger = triggerReason === "socket_reconnect"

      if (syncInFlightRef.current) {
        return syncInFlightRef.current
      }

      if (
        isReconnectTrigger &&
        timeSinceLastSync < RECONNECT_SYNC_COOLDOWN_MS
      ) {
        return
      }

      const syncPromise = (async () => {
        try {
          const response = await getNotificationsApi({ page: 0, size: NOTIFICATION_PAGE_SIZE })
          const incoming = response.notifications || []
          const incomingUnreadCount = response.unreadCount || 0

          const hasMore = response.hasMore === true
          const nextPage = typeof response.nextPage === "number" ? response.nextPage : null
          const windowCreatedAt = typeof response.windowCreatedAt === "string"
            ? response.windowCreatedAt
            : null

          hasMoreNotificationsRef.current = hasMore
          nextPageRef.current = nextPage
          windowCreatedAtRef.current = windowCreatedAt
          setHasMoreNotifications(hasMore)

          // Apply pending read scopes: if a notification is covered by an unconfirmed read scope,
          // preserve local isRead = true even if the server snapshot shows it as unread.
          const pendingScopes = pendingScopesRef.current
          const incomingWithScopeApplied = pendingScopes.length > 0
            ? incoming.map((item) =>
                !item.isRead && isCoveredByPendingScope(item, pendingScopes)
                  ? { ...item, isRead: true }
                  : item
              )
            : incoming

          const serverNewestTimestamp = getNewestTimestamp(incomingWithScopeApplied)
          const hasNewerRealtimeEvents =
            mode === "reconcile" &&
            serverNewestTimestamp < latestRealtimeTimestampRef.current

          const mergedNotifications = hasNewerRealtimeEvents
            ? mergeNotifications(
                incomingWithScopeApplied,
                notificationsRef.current.filter((item) =>
                  realtimeNotificationIdsRef.current.has(item.id) &&
                  toTimestamp(item.createdAt) >= latestRealtimeTimestampRef.current
                )
              )
            : mergeNotifications(incomingWithScopeApplied)

          const prunedNotifications = pruneStaleFriendRequests(
            mergedNotifications,
            incomingWithScopeApplied,
            serverNewestTimestamp,
            latestRealtimeTimestampRef.current
          )

          setNotificationsState(prunedNotifications)

          const unreadFloor = countUnreadNotifications(
            prunedNotifications,
            notificationModesByRoomRef.current,
            userId ?? null
          )
          const hasModeOverrides = hasRoomModeOverrides(notificationModesByRoomRef.current)

          setUnreadCount(() => {
            if (hasModeOverrides) {
              return unreadFloor
            }

            // When pending read scopes are active, the server unread count may not yet reflect
            // the optimistic read; trust the locally-computed floor in that case.
            if (pendingScopes.length > 0) {
              return unreadFloor
            }

            // API snapshot is authoritative baseline; floor protects against
            // undercount when mode filters or unresolved action-required items apply.
            return Math.max(unreadFloor, incomingUnreadCount)
          })
        } finally {
          lastSyncTimeRef.current = Date.now()
          lastSyncTriggerRef.current = triggerReason
          syncInFlightRef.current = null
        }
      })()

      syncInFlightRef.current = syncPromise
      return syncPromise
    },
    [setNotificationsState, userId]
  )

  const fetchNotifications = useCallback(async () => {
    await syncNotifications("replace", "manual_action")
  }, [syncNotifications])

  const loadMoreNotifications = useCallback(async () => {
    if (loadingMoreRef.current) {
      return
    }

    if (!hasMoreNotificationsRef.current || nextPageRef.current == null) {
      return
    }

    loadingMoreRef.current = true
    setIsLoadingMoreNotifications(true)

    try {
      const response = await getNotificationsApi({
        page: nextPageRef.current,
        size: NOTIFICATION_PAGE_SIZE,
        beforeCreatedAt: windowCreatedAtRef.current ?? undefined,
      })

      const incoming = response.notifications || []
      const incomingUnreadCount = response.unreadCount || 0

      const mergedNotifications = mergeNotifications(notificationsRef.current, incoming)
      setNotificationsState(mergedNotifications)

      const unreadFloor = countUnreadNotifications(
        mergedNotifications,
        notificationModesByRoomRef.current,
        userId ?? null
      )
      const hasModeOverrides = hasRoomModeOverrides(notificationModesByRoomRef.current)

      setUnreadCount(() => {
        if (hasModeOverrides || pendingScopesRef.current.length > 0) {
          return unreadFloor
        }

        return Math.max(unreadFloor, incomingUnreadCount)
      })

      const hasMore = response.hasMore === true
      const nextPage = typeof response.nextPage === "number" ? response.nextPage : null
      const windowCreatedAt = typeof response.windowCreatedAt === "string"
        ? response.windowCreatedAt
        : windowCreatedAtRef.current

      hasMoreNotificationsRef.current = hasMore
      nextPageRef.current = nextPage
      windowCreatedAtRef.current = windowCreatedAt
      setHasMoreNotifications(hasMore)
    } finally {
      loadingMoreRef.current = false
      setIsLoadingMoreNotifications(false)
    }
  }, [setNotificationsState, userId])

  const markRead = useCallback(async (id: string) => {
    let decremented = false

    setNotificationsState((prev) =>
      prev.map((item) => {
        if (item.id !== id || item.isRead) return item
        decremented = true
        return { ...item, isRead: true }
      })
    )

    if (decremented) {
      setUnreadCount((prev) => Math.max(0, prev - 1))
    }

    await markNotificationReadApi(id)
    await syncNotifications("replace", "post-mark-read")
  }, [setNotificationsState, syncNotifications])

  const markAllRead = useCallback(async () => {
    const scope: PendingReadScope = { type: "all" }
    pendingScopesRef.current = [...pendingScopesRef.current, scope]

    setNotificationsState((prev) =>
      prev.map((item) => (item.type === "FRIEND_REQUEST" ? item : { ...item, isRead: true }))
    )

    setUnreadCount(
      countUnreadNotifications(
        notificationsRef.current.map((item) => (item.type === "FRIEND_REQUEST" ? item : { ...item, isRead: true })),
        notificationModesByRoomRef.current,
        userId ?? null
      )
    )
    try {
      await markAllNotificationsReadApi()
      await syncNotifications("replace", "post-mark-read")
    } finally {
      pendingScopesRef.current = pendingScopesRef.current.filter((s) => s !== scope)
    }
  }, [setNotificationsState, syncNotifications, userId])

  const clearRoomNotifications = useCallback(async (roomId: string) => {
    if (!roomId) return

    const scope: PendingReadScope = { type: "room", roomId }
    pendingScopesRef.current = [...pendingScopesRef.current, scope]

    setNotificationsState((prev) =>
      prev.map((item) => {
        if (item.type === "FRIEND_REQUEST") return item
        if (!item.roomId) return item
        if (item.roomId !== roomId) return item
        if (item.isRead) return item
        return { ...item, isRead: true }
      })
    )

    setUnreadCount(
      countUnreadNotifications(
        notificationsRef.current.map((item) => {
          if (item.type === "FRIEND_REQUEST") return item
          if (!item.roomId) return item
          if (item.roomId !== roomId) return item
          return { ...item, isRead: true }
        }),
        notificationModesByRoomRef.current,
        userId ?? null
      )
    )

    try {
      await clearRoomNotificationsApi(roomId)
      await syncNotifications("replace", "post-mark-read")
    } finally {
      pendingScopesRef.current = pendingScopesRef.current.filter((s) => s !== scope)
    }
  }, [setNotificationsState, syncNotifications, userId])

  const fetchRoomNotificationMode = useCallback(async (roomId: string) => {
    const response = await getRoomMuteSettingsApi(roomId)
    const mode = normalizeRoomNotificationMode(response.mode, response.isMuted)

    setNotificationModesByRoom((prev) => ({
      ...prev,
      [roomId]: mode,
    }))

    return mode
  }, [])

  const setRoomNotificationMode = useCallback(async (roomId: string, mode: RoomNotificationMode) => {
    const previousMode = notificationModesByRoomRef.current[roomId] ?? "NO_RESTRICT"
    const optimisticModes = {
      ...notificationModesByRoomRef.current,
      [roomId]: mode,
    }

    setNotificationModesByRoom(optimisticModes)
    setUnreadCount(
      countUnreadNotifications(
        notificationsRef.current,
        optimisticModes,
        userId ?? null
      )
    )

    try {
      const response = await updateRoomNotificationModeApi(roomId, mode)
      const normalized = normalizeRoomNotificationMode(response.mode, response.isMuted)
      const confirmedModes = {
        ...optimisticModes,
        [roomId]: normalized,
      }
      setNotificationModesByRoom(confirmedModes)
      setUnreadCount(
        countUnreadNotifications(
          notificationsRef.current,
          confirmedModes,
          userId ?? null
        )
      )
    } catch (error) {
      const rollbackModes = {
        ...optimisticModes,
        [roomId]: previousMode,
      }
      setNotificationModesByRoom(rollbackModes)
      setUnreadCount(
        countUnreadNotifications(
          notificationsRef.current,
          rollbackModes,
          userId ?? null
        )
      )
      throw error
    }
  }, [userId])

  useEffect(() => {
    localStorage.setItem(ROOM_NOTIFICATION_MODE_STORAGE_KEY, JSON.stringify(notificationModesByRoom))
  }, [notificationModesByRoom])

  useEffect(() => {
    if (!accessToken) {
      latestRealtimeTimestampRef.current = 0
      realtimeNotificationIdsRef.current = new Set()
      hasMoreNotificationsRef.current = false
      nextPageRef.current = null
      windowCreatedAtRef.current = null
      loadingMoreRef.current = false
      setHasMoreNotifications(false)
      setIsLoadingMoreNotifications(false)
      disconnectNotificationSocket()
      return
    }

    connectNotificationSocket()

    const unsubscribeOpen = onNotificationSocketOpen(() => {
      void syncNotifications("reconcile", "socket_reconnect").catch(() => {})
    })

    const unsubscribeEvent = onNotificationEvent((event) => {
      console.log("[notification-store] Socket event received", {
        type: event.type,
        knownType: event.type === NotificationEventType.NOTIFICATION_NEW || event.type === NotificationEventType.UNREAD_COUNT_UPDATE,
        rawData: event.data,
      })
      if (event.type === NotificationEventType.NOTIFICATION_NEW) {
        const payload = event.data as Notification
        console.log("[notification-store] NOTIFICATION_NEW raw payload", payload)
        applyRealtimeNotification(payload)
        return
      }

      if (event.type === NotificationEventType.UNREAD_COUNT_UPDATE) {
        const payload = event.data as UnreadCountResponse
        if (typeof payload?.unreadCount === "number") {
          const unreadFloor = countUnreadNotifications(
            notificationsRef.current,
            notificationModesByRoomRef.current,
            userId ?? null
          )
          const hasModeOverrides = hasRoomModeOverrides(notificationModesByRoomRef.current)
          if (hasModeOverrides || pendingScopesRef.current.length > 0) {
            setUnreadCount(unreadFloor)
          } else {
            // Guard against stale websocket unread increases after refresh.
            // Increases should come from NOTIFICATION_NEW or next API snapshot.
            setUnreadCount((prev) => {
              const candidate = Math.max(unreadFloor, payload.unreadCount)
              if (candidate > prev) {
                return unreadFloor
              }

              return candidate
            })
          }
        }
      }
    })

    return () => {
      unsubscribeOpen()
      unsubscribeEvent()
      disconnectNotificationSocket()
    }
  }, [accessToken, applyRealtimeNotification, syncNotifications, userId])

  return (
    <NotificationContext.Provider
      value={{
        notifications,
        unreadCount,
        hasMoreNotifications,
        isLoadingMoreNotifications,
        notificationModesByRoom,
        fetchNotifications,
        loadMoreNotifications,
        markRead,
        markAllRead,
        clearRoomNotifications,
        fetchRoomNotificationMode,
        setRoomNotificationMode,
      }}
    >
      {children}
    </NotificationContext.Provider>
  )
}

export function useNotifications() {
  const ctx = useContext(NotificationContext)
  if (!ctx) throw new Error("useNotifications must be used inside NotificationProvider")
  return ctx
}
