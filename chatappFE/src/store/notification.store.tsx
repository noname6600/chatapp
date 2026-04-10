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
  getRoomMuteSettingsApi,
  markAllNotificationsReadApi,
  markNotificationReadApi,
  muteRoomApi,
  unmuteRoomApi,
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

import type {
  Notification,
  UnreadCountResponse,
} from "../types/notification"

interface NotificationContextType {
  notifications: Notification[]
  unreadCount: number
  mutesByRoom: Record<string, boolean>
  fetchNotifications: () => Promise<void>
  markRead: (id: string) => Promise<void>
  markAllRead: () => Promise<void>
  fetchRoomMute: (roomId: string) => Promise<boolean>
  toggleRoomMute: (roomId: string, muted: boolean) => Promise<void>
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined)
const ROOM_MUTE_STORAGE_KEY = "notification_mutes_by_room"

type SyncMode = "replace" | "reconcile"
type SyncTriggerReason = "initial_load" | "socket_reconnect" | "manual_action" | "post-mark-read"

// Constants for fetch throttling
const RECONNECT_SYNC_COOLDOWN_MS = 2000 // 2 second minimum interval for reconnect-triggered syncs

const isValidRealtimeNotification = (notification: Notification): boolean => {
  if (notification.type !== "MENTION") {
    return true
  }

  // Mention UI entries require concrete target context.
  return Boolean(notification.referenceId && notification.roomId)
}

const shouldIncrementUnread = (
  notification: Notification,
  mutesByRoom: Record<string, boolean>,
  currentUserId: string | null
) => {
  if (notification.isRead) return false

  if (
    isFeatureEnabled("enableSelfMessageUnreadExclusion") &&
    currentUserId != null &&
    notification.senderId === currentUserId
  ) {
    return false
  }

  const isRoomNotification =
    notification.type === "MESSAGE" || notification.type === "MENTION"

  if (!isRoomNotification || !notification.roomId) {
    return true
  }

  return !Boolean(mutesByRoom[notification.roomId])
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

  return [...map.values()].sort(
    (a, b) => toTimestamp(b.createdAt) - toTimestamp(a.createdAt)
  )
}

const getNewestTimestamp = (items: Notification[]) => {
  if (items.length === 0) return 0
  return items.reduce((maxTs, item) => Math.max(maxTs, toTimestamp(item.createdAt)), 0)
}

const countUnreadNotifications = (
  items: Notification[],
  mutesByRoom: Record<string, boolean>,
  currentUserId: string | null
) => {
  return items.reduce((count, item) => {
    if (!shouldIncrementUnread(item, mutesByRoom, currentUserId)) {
      return count
    }

    return count + 1
  }, 0)
}

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { accessToken, userId } = useAuth()

  const [notifications, setNotifications] = useState<Notification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [mutesByRoom, setMutesByRoom] = useState<Record<string, boolean>>({})

  const mutesByRoomRef = useRef<Record<string, boolean>>({})
  const notificationsRef = useRef<Notification[]>([])
  mutesByRoomRef.current = mutesByRoom
  notificationsRef.current = notifications
  const latestRealtimeTimestampRef = useRef(0)
  
  // Refs for sync fetch throttling (tasks 2.1-2.4)
  const syncInFlightRef = useRef<Promise<void> | null>(null)
  const lastSyncTimeRef = useRef(0)
  const lastSyncTriggerRef = useRef<SyncTriggerReason | null>(null)

  const setNotificationsState = useCallback(
    (updater: Notification[] | ((prev: Notification[]) => Notification[])) => {
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
    if (!isValidRealtimeNotification(payload)) {
      return
    }

    latestRealtimeTimestampRef.current = Math.max(
      latestRealtimeTimestampRef.current,
      toTimestamp(payload.createdAt)
    )

    const isNew = !notificationsRef.current.some((item) => item.id === payload.id)

    let mergedNotifications: Notification[] = []
    setNotificationsState((prev) => {
      mergedNotifications = mergeNotifications([payload], prev)
      return mergedNotifications
    })

    const unreadFloor = countUnreadNotifications(
      mergedNotifications,
      mutesByRoomRef.current,
      userId ?? null
    )

    setUnreadCount((prev) => {
      const next =
        isNew && shouldIncrementUnread(payload, mutesByRoomRef.current, userId ?? null)
          ? prev + 1
          : prev

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

      // Task 2.1: Single-flight guard - return existing in-flight promise
      if (syncInFlightRef.current) {
        console.debug("[notification-sync] Fetch already in-flight, coalescing request", {
          triggerReason,
          previousTrigger: lastSyncTriggerRef.current,
        })
        return syncInFlightRef.current
      }

      // Task 2.2: Apply reconnect-trigger cooldown
      if (
        isReconnectTrigger &&
        timeSinceLastSync < RECONNECT_SYNC_COOLDOWN_MS
      ) {
        console.debug("[notification-sync] Skipping reconnect sync due to cooldown", {
          timeSinceLastSyncMs: timeSinceLastSync,
          cooldownMs: RECONNECT_SYNC_COOLDOWN_MS,
        })
        return
      }

      // Task 2.4: Log fetch execution decision
      console.log("[notification-sync] Executing fetch request", {
        triggerReason,
        mode,
        timeSinceLastSyncMs: timeSinceLastSync,
      })

      // Create single-flight promise
      const syncPromise = (async () => {
        try {
          const response = await getNotificationsApi()
          const incoming = response.notifications || []
          const incomingUnreadCount = response.unreadCount || 0

          const serverNewestTimestamp = getNewestTimestamp(incoming)
          const hasNewerRealtimeEvents =
            mode === "reconcile" &&
            serverNewestTimestamp < latestRealtimeTimestampRef.current

          let mergedNotifications: Notification[] = []
          setNotificationsState((prev) => {
            if (hasNewerRealtimeEvents) {
              mergedNotifications = mergeNotifications(incoming, prev)
              return mergedNotifications
            }
            mergedNotifications = mergeNotifications(incoming)
            return mergedNotifications
          })

          const unreadFloor = countUnreadNotifications(
            mergedNotifications,
            mutesByRoomRef.current,
            userId ?? null
          )

          setUnreadCount((prev) => {
            if (hasNewerRealtimeEvents) {
              return Math.max(unreadFloor, prev, incomingUnreadCount)
            }
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
    setNotificationsState((prev) => prev.map((item) => ({ ...item, isRead: true })))
    setUnreadCount(0)
    await markAllNotificationsReadApi()
    await syncNotifications("replace", "post-mark-read")
  }, [setNotificationsState, syncNotifications])

  const fetchRoomMute = useCallback(async (roomId: string) => {
    const response = await getRoomMuteSettingsApi(roomId)

    setMutesByRoom((prev) => ({
      ...prev,
      [roomId]: response.isMuted,
    }))

    return response.isMuted
  }, [])

  const toggleRoomMute = useCallback(async (roomId: string, muted: boolean) => {
    const previousMuted = Boolean(mutesByRoomRef.current[roomId])

    setMutesByRoom((prev) => ({
      ...prev,
      [roomId]: muted,
    }))

    try {
      if (muted) {
        await muteRoomApi(roomId)
      } else {
        await unmuteRoomApi(roomId)
      }
    } catch (error) {
      setMutesByRoom((prev) => ({
        ...prev,
        [roomId]: previousMuted,
      }))
      throw error
    }
  }, [])

  useEffect(() => {
    localStorage.setItem(ROOM_MUTE_STORAGE_KEY, JSON.stringify(mutesByRoom))
  }, [mutesByRoom])

  useEffect(() => {
    if (!accessToken) {
      latestRealtimeTimestampRef.current = 0
      disconnectNotificationSocket()
      return
    }

    connectNotificationSocket()

    const unsubscribeOpen = onNotificationSocketOpen(() => {
      void syncNotifications("reconcile", "socket_reconnect").catch(() => {})
    })

    const unsubscribeEvent = onNotificationEvent((event) => {
      if (event.type === NotificationEventType.NOTIFICATION_NEW) {
        const payload = event.data as Notification
        applyRealtimeNotification(payload)
        return
      }

      if (event.type === NotificationEventType.UNREAD_COUNT_UPDATE) {
        const payload = event.data as UnreadCountResponse
        if (typeof payload?.unreadCount === "number") {
          const unreadFloor = countUnreadNotifications(
            notificationsRef.current,
            mutesByRoomRef.current,
            userId ?? null
          )

          setUnreadCount(Math.max(unreadFloor, payload.unreadCount))
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
        mutesByRoom,
        fetchNotifications,
        markRead,
        markAllRead,
        fetchRoomMute,
        toggleRoomMute,
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