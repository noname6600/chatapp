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

const ROOM_MUTE_STORAGE_KEY = "notification_mutes_by_room"

const toTimestamp = (value: string | null | undefined): number => {
  if (!value) return 0

  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

const getRoomLatestTimestamp = (room: Room): number => {
  return toTimestamp(room.latestMessageAt ?? room.lastMessage?.createdAt ?? null)
}

const isRoomMuted = (roomId: string): boolean => {
  try {
    const raw = localStorage.getItem(ROOM_MUTE_STORAGE_KEY)
    if (!raw) return false

    const parsed = JSON.parse(raw) as Record<string, boolean>
    return Boolean(parsed[roomId])
  } catch {
    return false
  }
}

interface RoomContextType {
  roomsById: Record<string, Room>
  roomOrder: string[]
  loadRooms: () => Promise<void>
  addRoom: (room: Room) => void
  updateRoom: (room: Room) => void
  markRoomRead: (roomId: string) => Promise<void>
  reconcileRoomState: () => Promise<void>
}

const RoomContext = createContext<RoomContextType | undefined>(undefined)

export function RoomProvider({ children }: { children: React.ReactNode }) {
  const { accessToken, userId } = useAuth()
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
    }
  }, [resetUnreadLocal])

  // Reconcile room state with backend snapshot (used after websocket reconnect/reload).
  const reconcileRoomState = useCallback(async () => {
    // Reload fresh room list from backend to get authoritative unread counts.
    try {
      const freshRooms = await getMyRooms()

      setRoomsById(prev => {
        const updated = { ...prev }

        for (const freshRoom of freshRooms) {
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

        setRoomOrder((prevOrder) => sortRoomIdsByRecency(updated, prevOrder))

        return updated
      })

      // Reset processed messages after reconciliation to allow websocket updates.
      processedMessagesRef.current = {}
    } catch (error) {
      console.error("Failed to reconcile room state:", error)
    }
  }, [])

  const scheduleMissingRoomReconcile = useCallback(() => {
    if (missingRoomReconcileTimeoutRef.current != null) {
      return
    }

    missingRoomReconcileTimeoutRef.current = window.setTimeout(() => {
      missingRoomReconcileTimeoutRef.current = null
      void reconcileRoomState()
    }, 120)
  }, [reconcileRoomState])

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

      // First-message edge case: incoming message can target a room not yet in local
      // list state (for example, first-ever DM). Trigger a debounced reconciliation so
      // the new room appears without requiring manual page refresh.
      if (!roomsByIdRef.current[msg.roomId]) {
        scheduleMissingRoomReconcile()
        return
      }

      // Deterministic merge: skip if this message was already processed.
      const processedSet = getProcessedMessages(msg.roomId)
      if (processedSet.has(msg.messageId)) {
        // Event already processed, skip to avoid double-increment.
        return
      }

      // Mark this message as processed for future events.
      processedSet.add(msg.messageId)

      const preview = buildPreview(msg)

      setRoomsById(prev => {
        const room = prev[msg.roomId]
        if (!room) return prev

        const isSender = userId != null && msg.senderId === userId
        const muted = isRoomMuted(msg.roomId)

        const senderName = resolveLastMessageSenderName({
          room,
          senderId: msg.senderId,
          currentUserId: userId,
          usersById: users,
        })

        const roomLatestTs = toTimestamp(room.latestMessageAt ?? room.lastMessage?.createdAt ?? null)
        const messageTs = toTimestamp(msg.createdAt)
        const shouldPromoteAsLatest = messageTs >= roomLatestTs

        // Self-message unread exclusion (can be disabled via feature flag)
        const excludeSelfFromUnread = isFeatureEnabled("enableSelfMessageUnreadExclusion") && isSender;
        const unreadCount =
          excludeSelfFromUnread || muted
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
            content: preview,
            createdAt: msg.createdAt
          }
        }

        return {
          ...prev,
          [msg.roomId]: nextRoom
        }
      })

      scheduleSortRoomOrder()
    })
  }, [getProcessedMessages, scheduleMissingRoomReconcile, scheduleSortRoomOrder, userId, users])

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
