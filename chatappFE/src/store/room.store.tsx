import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef
} from "react"

import { getMyRooms, markRoomReadApi } from "../api/room.service"
import { onChatEvent, onSocketOpen, subscribeRoom } from "../websocket/chat.socket"
import { ChatEventType } from "../constants/chatEvents"
import { useAuth } from "./auth.store"
import { useNotifications } from "./notification.store"
import { useUserStore } from "./user.store"
import { usePopulateUserCache } from "../hooks/usePopulateUserCache"
import { isFeatureEnabled } from "../config/featureFlags"
import {
  normalizeRoomForList,
  resolveLastMessageSenderName,
  sortRoomIdsByRecency,
} from "../utils/roomListIntegrity"

import type { Room } from "../types/room"
import type { ChatMessage } from "../types/message"
import type { RoomNotificationMode } from "../types/notification"
import {
  normalizeRoomNotificationMode,
  shouldDeliverRoomEventByMode,
} from "../utils/notificationModePolicy"

const ROOM_NOTIFICATION_MODE_STORAGE_KEY = "notification_modes_by_room"
const LEGACY_ROOM_MUTE_STORAGE_KEY = "notification_mutes_by_room"

const toTimestamp = (value: string | null | undefined): number => {
  if (!value) return 0

  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

const getRoomLatestTimestamp = (room: Room): number => {
  return toTimestamp(room.latestMessageAt ?? room.lastMessage?.createdAt ?? null)
}

const getRoomNotificationMode = (roomId: string): RoomNotificationMode => {
  try {
    const raw = localStorage.getItem(ROOM_NOTIFICATION_MODE_STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Record<string, RoomNotificationMode>
      const mode = parsed[roomId]
      return normalizeRoomNotificationMode(mode)
    }
  } catch {
    // Fallback to legacy boolean storage below.
  }

  try {
    const legacy = localStorage.getItem(LEGACY_ROOM_MUTE_STORAGE_KEY)
    if (!legacy) return "NO_RESTRICT"
    const parsed = JSON.parse(legacy) as Record<string, boolean>
    return parsed[roomId] ? "NOTHING" : "NO_RESTRICT"
  } catch {
    return "NO_RESTRICT"
  }
}

interface RoomContextType {
  roomsById: Record<string, Room>
  roomOrder: string[]
  loadRooms: () => Promise<void>
  addRoom: (room: Room) => void
  updateRoom: (room: Room) => void
  removeRoom: (roomId: string) => void
  markRoomRead: (roomId: string) => Promise<void>
  reconcileRoomState: () => Promise<void>
}

const RoomContext = createContext<RoomContextType | undefined>(undefined)

export function RoomProvider({ children }: { children: React.ReactNode }) {
  const { accessToken, userId } = useAuth()
  const { clearRoomNotifications } = useNotifications()
  const users = useUserStore((state) => state.users)
  const usersRef = useRef(users)
  usersRef.current = users

  const [roomsById, setRoomsById] = useState<Record<string, Room>>({})
  const [roomOrder, setRoomOrder] = useState<string[]>([])
  const [roomsLoaded, setRoomsLoaded] = useState(false)
  const roomsByIdRef = useRef<Record<string, Room>>({})
  roomsByIdRef.current = roomsById
  const sortDebounceRef = useRef<number | null>(null)
  const missingRoomReconcileTimeoutRef = useRef<number | null>(null)
  const subscribedRoomIdsRef = useRef<Set<string>>(new Set())

  // Track processed MESSAGE_SENT events to prevent duplicate unread increments.
  // Maps roomId -> Set of processed messageIds.
  const processedMessagesRef = useRef<Record<string, Set<string>>>({})
  const getProcessedMessages = useCallback((roomId: string): Set<string> => {
    if (!processedMessagesRef.current[roomId]) {
      processedMessagesRef.current[roomId] = new Set()
    }
    return processedMessagesRef.current[roomId]
  }, [])

  const scheduleSortRoomOrder = useCallback(() => {
    if (sortDebounceRef.current != null) {
      clearTimeout(sortDebounceRef.current)
    }

    sortDebounceRef.current = window.setTimeout(() => {
      sortDebounceRef.current = null

      setRoomOrder((prev) => sortRoomIdsByRecency(roomsByIdRef.current, prev))
    }, 300)
  }, [])

  // Populate user cache with friends and group members after rooms load.
  usePopulateUserCache(userId, roomsById, roomsLoaded)

  const loadRooms = useCallback(async () => {
    if (!accessToken || !userId) {
      setRoomsById({})
      setRoomOrder([])
      setRoomsLoaded(false)
      processedMessagesRef.current = {}
      return
    }

    const rooms = await getMyRooms()

    const map: Record<string, Room> = {}
    const order: string[] = []

    for (const r of rooms) {
      map[r.id] = normalizeRoomForList(r, userId, usersRef.current)
      order.push(r.id)
    }

    setRoomsById(map)
    setRoomOrder(order.length > 0 ? sortRoomIdsByRecency(map, order) : [])
    setRoomsLoaded(true)

    // Reset processed messages tracking on refresh/load to ensure fresh reconciliation.
    processedMessagesRef.current = {}
  }, [accessToken, userId])

  useEffect(() => {
    void loadRooms().catch((error) => {
      console.error("Failed to load rooms:", error)
    })

    return () => {
      if (sortDebounceRef.current != null) {
        clearTimeout(sortDebounceRef.current)
      }
    }
  }, [loadRooms])

  useEffect(() => {
    const knownRoomIds = Object.keys(roomsById)
    if (knownRoomIds.length === 0) {
      return
    }

    for (const roomId of knownRoomIds) {
      if (subscribedRoomIdsRef.current.has(roomId)) {
        continue
      }

      subscribeRoom(roomId)
      subscribedRoomIdsRef.current.add(roomId)
    }
  }, [roomsById])

  const addRoom = useCallback((room: Room) => {
    const normalizedRoom = normalizeRoomForList(room, userId, usersRef.current)

    setRoomsById(prev => {
      const nextMap = {
        ...prev,
        [normalizedRoom.id]: normalizedRoom,
      }

      setRoomOrder((prevOrder) => {
        const withInserted = prevOrder.includes(normalizedRoom.id)
          ? prevOrder
          : [normalizedRoom.id, ...prevOrder]

        return sortRoomIdsByRecency(nextMap, withInserted)
      })

      return nextMap
    })
  }, [userId])

  const updateRoom = useCallback((room: Room) => {
    setRoomsById(prev => {
      const merged = {
        ...prev[room.id],
        ...room
      }

      const normalizedRoom = normalizeRoomForList(merged, userId, usersRef.current)

      const nextMap = {
        ...prev,
        [room.id]: normalizedRoom
      }

      setRoomOrder((prevOrder) => sortRoomIdsByRecency(nextMap, prevOrder))

      return nextMap
    })
  }, [userId])

  const removeRoom = useCallback((roomId: string) => {
    if (!roomId) return

    setRoomsById((prev) => {
      if (!prev[roomId]) return prev

      const nextMap = { ...prev }
      delete nextMap[roomId]
      return nextMap
    })

    setRoomOrder((prev) => prev.filter((id) => id !== roomId))
    delete processedMessagesRef.current[roomId]
  }, [])

  const resetUnreadLocal = useCallback((roomId: string) => {
    setRoomsById(prev => {
      const room = prev[roomId]
      if (!room) return prev
      if (room.unreadCount === 0) return prev

      return {
        ...prev,
        [roomId]: {
          ...room,
          unreadCount: 0
        }
      }
    })
  }, [])

  const markRoomRead = useCallback(async (roomId: string) => {
    if (!roomId) return

    try {
      await markRoomReadApi(roomId)
    } finally {
      // Keep FE responsive even if network fails; backend will reconcile on next /rooms/my load.
      resetUnreadLocal(roomId)

      // When user is at latest (mark-read interaction), clear eligible room-scoped notifications too.
      if (accessToken) {
        await clearRoomNotifications(roomId).catch(() => {})
      }
    }
  }, [accessToken, clearRoomNotifications, resetUnreadLocal])

  // Reconcile room state with backend snapshot (used after websocket reconnect/reload).
  const reconcileRoomState = useCallback(async () => {
    // Reload fresh room list from backend to get authoritative unread counts.
    try {
      const freshRooms = await getMyRooms()

      setRoomsById(prev => {
        const updated: Record<string, Room> = {}
        const nextOrder: string[] = []

        for (const freshRoom of freshRooms) {
          nextOrder.push(freshRoom.id)
          const existing = prev[freshRoom.id]
          if (existing) {
            const existingLatestTs = getRoomLatestTimestamp(existing)
            const freshLatestTs = getRoomLatestTimestamp(freshRoom)
            const shouldApplyFreshPreview = freshLatestTs >= existingLatestTs

            // Replace stale values with backend snapshot (not additive).
            updated[freshRoom.id] = {
              ...existing,
              latestMessageAt: shouldApplyFreshPreview
                ? freshRoom.latestMessageAt ?? freshRoom.lastMessage?.createdAt ?? existing.latestMessageAt
                : existing.latestMessageAt,
              unreadCount: freshRoom.unreadCount ?? 0,
              lastMessage: shouldApplyFreshPreview
                ? freshRoom.lastMessage ?? existing.lastMessage
                : existing.lastMessage,
            }
          } else {
            updated[freshRoom.id] = normalizeRoomForList(freshRoom, userId, usersRef.current)
          }
        }

        setRoomOrder(
          nextOrder.length > 0 ? sortRoomIdsByRecency(updated, nextOrder) : []
        )

        return updated
      })

      // Reset processed messages after reconciliation to allow websocket updates.
      processedMessagesRef.current = {}
    } catch (error) {
      console.error("Failed to reconcile room state:", error)
    }
  }, [userId])

  const scheduleMissingRoomReconcile = useCallback(() => {
    if (missingRoomReconcileTimeoutRef.current != null) {
      return
    }

    missingRoomReconcileTimeoutRef.current = window.setTimeout(() => {
      missingRoomReconcileTimeoutRef.current = null
      void reconcileRoomState()
    }, 120)
  }, [reconcileRoomState])

  const applyIncomingMessageToRoom = useCallback(
    (room: Room, msg: ChatMessage): Room => {
      const isSender = userId != null && msg.senderId === userId
      const mode = getRoomNotificationMode(msg.roomId)
      const isMentioned = Boolean(userId && msg.mentionedUserIds?.includes(userId))
      const suppressUnreadByMode = !shouldDeliverRoomEventByMode(mode, isMentioned)

      const senderName = resolveLastMessageSenderName({
        room,
        senderId: msg.senderId,
        currentUserId: userId,
        usersById: usersRef.current,
      })

      const roomLatestTs = toTimestamp(room.latestMessageAt ?? room.lastMessage?.createdAt ?? null)
      const messageTs = toTimestamp(msg.createdAt)
      const shouldPromoteAsLatest = messageTs >= roomLatestTs

      const excludeSelfFromUnread = isFeatureEnabled("enableSelfMessageUnreadExclusion") && isSender
      const unreadCount =
        excludeSelfFromUnread || suppressUnreadByMode
          ? (room.unreadCount ?? 0)
          : (room.unreadCount ?? 0) + 1

      const nextRoom: Room = {
        ...room,
        unreadCount,
      }

      if (shouldPromoteAsLatest) {
        nextRoom.latestMessageAt = msg.createdAt
        nextRoom.lastMessage = {
          id: msg.messageId,
          senderId: msg.senderId,
          senderName,
          content: buildPreview(msg),
          createdAt: msg.createdAt,
        }
      }

      return nextRoom
    },
    [userId]
  )

  const buildPreview = (msg: ChatMessage): string => {
    if (msg.deleted) return "[Message deleted]"

    if (msg.blocks && msg.blocks.length > 0) {
      return msg.blocks
        .map((block) => {
          if (block.type === "TEXT") {
            return block.text?.trim() ?? ""
          }

          if (block.type === "ROOM_INVITE") {
            const roomName = block.roomInvite?.roomName?.trim()
            return roomName ? `[Group Invite: ${roomName}]` : "[Group Invite]"
          }

          switch (block.attachment?.type) {
            case "IMAGE": return "[Image]"
            case "VIDEO": return "[Video]"
            case "FILE": return "[File]"
            default: return "[Attachment]"
          }
        })
        .filter(Boolean)
        .join(" ") || "[Message]"
    }

    if (msg.type === "TEXT") return msg.content ?? ""

    if (msg.type === "ATTACHMENT") {
      const first = msg.attachments?.[0]
      if (!first) return "[Attachment]"

      switch (first.type) {
        case "IMAGE": return "[Image]"
        case "VIDEO": return "[Video]"
        case "FILE": return "[File]"
        default: return "[Attachment]"
      }
    }

    if (msg.type === "MIXED") return msg.content ?? "[Attachment]"

    return "[Message]"
  }

  useEffect(() => {
    return onChatEvent((event) => {
      if (event.type === ChatEventType.REACTION_UPDATED) {
        // Reactions do not change room last-message preview text.
        return
      }

      if (event.type === ChatEventType.MESSAGE_EDITED) {
        const payload = event.payload

        setRoomsById(prev => {
          const room = prev[payload.roomId]
          if (!room || room.lastMessage?.id !== payload.messageId) return prev

          return {
            ...prev,
            [payload.roomId]: {
              ...room,
              lastMessage: {
                ...room.lastMessage,
                content: payload.content ?? ""
              }
            }
          }
        })

        return
      }

      if (event.type === ChatEventType.MESSAGE_DELETED) {
        const payload = event.payload

        setRoomsById(prev => {
          const room = prev[payload.roomId]
          if (!room || room.lastMessage?.id !== payload.messageId) return prev

          return {
            ...prev,
            [payload.roomId]: {
              ...room,
              lastMessage: {
                ...room.lastMessage,
                content: "[Message deleted]"
              }
            }
          }
        })

        return
      }

      if (event.type !== ChatEventType.MESSAGE_SENT) {
        return
      }

      const msg = event.payload

      // Deterministic merge: skip if this message was already processed.
      const processedSet = getProcessedMessages(msg.roomId)
      if (processedSet.has(msg.messageId)) {
        return
      }

      // First-message edge case: incoming message can target a room not yet in local
      // list state (for example, first-ever DM). Create a lightweight local placeholder
      // immediately so the first message is visible in realtime, then reconcile full data.
      if (!roomsByIdRef.current[msg.roomId]) {
        setRoomsById((prev) => {
          const existing = prev[msg.roomId]
          if (existing) return prev

          const placeholder: Room = {
            id: msg.roomId,
            type: "PRIVATE",
            name: "New message",
            avatarUrl: null,
            createdBy: msg.senderId,
            createdAt: msg.createdAt,
            myRole: "MEMBER",
            unreadCount: 0,
            latestMessageAt: null,
            otherUserId: msg.senderId,
            lastMessage: null,
          }

          const hydrated = applyIncomingMessageToRoom(placeholder, msg)

          const nextMap = {
            ...prev,
            [msg.roomId]: hydrated,
          }

          setRoomOrder((prevOrder) => {
            const withInserted = prevOrder.includes(msg.roomId)
              ? prevOrder
              : [msg.roomId, ...prevOrder]
            return sortRoomIdsByRecency(nextMap, withInserted)
          })

          return nextMap
        })

        if (!subscribedRoomIdsRef.current.has(msg.roomId)) {
          subscribeRoom(msg.roomId)
          subscribedRoomIdsRef.current.add(msg.roomId)
        }

        processedSet.add(msg.messageId)
        scheduleMissingRoomReconcile()
        return
      }

      // Mark this message as processed for future events.
      processedSet.add(msg.messageId)

      setRoomsById(prev => {
        const room = prev[msg.roomId]
        if (!room) return prev

        const nextRoom = applyIncomingMessageToRoom(room, msg)

        return {
          ...prev,
          [msg.roomId]: nextRoom
        }
      })

      scheduleSortRoomOrder()
    })
  }, [applyIncomingMessageToRoom, getProcessedMessages, scheduleMissingRoomReconcile, scheduleSortRoomOrder])

  // Reconcile room state when websocket reconnects.
  useEffect(() => {
    const unsubscribe = onSocketOpen(() => {
      // Schedule reconciliation asynchronously to avoid calling during render.
      setTimeout(() => {
        reconcileRoomState()
      }, 100)
    })

    return () => {
      unsubscribe()
    }
  }, [reconcileRoomState])

  useEffect(() => {
    return () => {
      if (missingRoomReconcileTimeoutRef.current != null) {
        clearTimeout(missingRoomReconcileTimeoutRef.current)
      }
    }
  }, [])

  return (
    <RoomContext.Provider
      value={{
        roomsById,
        roomOrder,
        loadRooms,
        addRoom,
        updateRoom,
        removeRoom,
        markRoomRead,
        reconcileRoomState
      }}
    >
      {children}
    </RoomContext.Provider>
  )
}

export const useRooms = () => {
  const ctx = useContext(RoomContext)

  if (!ctx)
    throw new Error("useRooms must be used inside RoomProvider")

  return ctx
}
