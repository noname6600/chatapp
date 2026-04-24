import { create } from "zustand"

import type {
  PresenceSelfState,
  PresenceStatus,
  PresenceUserState,
} from "../types/presence"

export type UserId = string
export type RoomId = string

const TYPING_TIMEOUT_MS = 6000
const typingTimers = new Map<string, number>()

const typingTimerKey = (roomId: string, userId: string) => `${roomId}:${userId}`

const buildVisibleUserMap = (
  users: Record<UserId, PresenceStatus>
): Record<UserId, true> => {
  const visible: Record<UserId, true> = {}

  Object.entries(users).forEach(([userId, status]) => {
    if (status !== "OFFLINE") {
      visible[userId] = true
    }
  })

  return visible
}

export interface PresenceState {
  onlineUsers: Record<UserId, true>
  onlineUsersByRoom: Record<RoomId, Record<UserId, true>>
  typingByRoom: Record<RoomId, Record<UserId, true>>
  userStatuses: Record<UserId, PresenceStatus>
  roomUserStatuses: Record<RoomId, Record<UserId, PresenceStatus>>
  selfPresence: PresenceSelfState | null

  setSelfPresence: (presence: PresenceSelfState | null) => void
  setGlobalPresence: (users: PresenceUserState[]) => void
  setUserStatus: (userId: UserId, status: PresenceStatus) => void
  clearAllOnline: () => void
  getUserStatus: (userId: UserId) => PresenceStatus
  isUserOnline: (userId: UserId) => boolean

  setRoomPresence: (roomId: RoomId, users: PresenceUserState[]) => void
  clearRoomOnline: (roomId: RoomId) => void
  getOnlineUsersInRoom: (roomId: RoomId) => UserId[]
  getRoomUserStatus: (roomId: RoomId, userId: UserId) => PresenceStatus

  setUserTyping: (roomId: RoomId, userId: UserId) => void
  setUserStopTyping: (roomId: RoomId, userId: UserId) => void
  clearRoomTyping: (roomId: RoomId) => void
  clearUserTypingEverywhere: (userId: UserId) => void
  getTypingUsers: (roomId: RoomId) => UserId[]
}

export const selectMemberStatusForRoom = (
  state: PresenceState,
  roomId: RoomId,
  userId: UserId
): PresenceStatus => state.roomUserStatuses[roomId]?.[userId] ?? state.userStatuses[userId] ?? "OFFLINE"

export const selectTypingUsersForRoom = (
  state: PresenceState,
  roomId: RoomId
): UserId[] => Object.keys(state.typingByRoom[roomId] ?? {})

export const usePresenceStore = create<PresenceState>((set, get) => ({
  onlineUsers: {},
  onlineUsersByRoom: {},
  typingByRoom: {},
  userStatuses: {},
  roomUserStatuses: {},
  selfPresence: null,

  setSelfPresence: (presence) => {
    if (!presence) {
      set({ selfPresence: null })
      return
    }
    const myId = localStorage.getItem("my_user_id")
    set((state) => {
      // Prefer the already-known live status (from WS events) over possibly stale REST self snapshot.
      const resolvedSelfStatus =
        myId && state.userStatuses[myId]
          ? state.userStatuses[myId]
          : presence.effectiveStatus

      const userStatuses = myId
        ? { ...state.userStatuses, [myId]: resolvedSelfStatus }
        : state.userStatuses
      return {
        selfPresence: { ...presence, effectiveStatus: resolvedSelfStatus },
        userStatuses,
        onlineUsers: myId ? buildVisibleUserMap(userStatuses) : state.onlineUsers,
      }
    })
  },

  setGlobalPresence: (users) =>
    set((state) => {
      const myId = localStorage.getItem("my_user_id")
      const nextStatuses: Record<UserId, PresenceStatus> = {
        ...state.userStatuses,
      }

      users.forEach((user) => {
        nextStatuses[user.userId] = user.status
      })

      // Some snapshots may not include the current user; keep last known self status.
      if (myId && !nextStatuses[myId]) {
        nextStatuses[myId] =
          state.userStatuses[myId] ?? state.selfPresence?.effectiveStatus ?? "ONLINE"
      }

      const myStatus = myId ? nextStatuses[myId] : undefined

      return {
        userStatuses: nextStatuses,
        onlineUsers: buildVisibleUserMap(nextStatuses),
        selfPresence:
          state.selfPresence && myStatus
            ? { ...state.selfPresence, effectiveStatus: myStatus }
            : state.selfPresence,
      }
    }),

  setUserStatus: (userId, status) =>
    set((state) => {
      const myId = localStorage.getItem("my_user_id")
      const userStatuses = {
        ...state.userStatuses,
        [userId]: status,
      }

      const roomUserStatuses: Record<RoomId, Record<UserId, PresenceStatus>> = {
        ...state.roomUserStatuses,
      }

      const onlineUsersByRoom: Record<RoomId, Record<UserId, true>> = {
        ...state.onlineUsersByRoom,
      }

      Object.entries(roomUserStatuses).forEach(([roomId, users]) => {
        if (!users[userId]) {
          return
        }

        const nextUsers = {
          ...users,
          [userId]: status,
        }

        roomUserStatuses[roomId] = nextUsers
        onlineUsersByRoom[roomId] = buildVisibleUserMap(nextUsers)
      })

      return {
        userStatuses,
        onlineUsers: buildVisibleUserMap(userStatuses),
        selfPresence:
          state.selfPresence && myId && userId === myId
            ? { ...state.selfPresence, effectiveStatus: status }
            : state.selfPresence,
        roomUserStatuses,
        onlineUsersByRoom,
        typingByRoom:
          status === "OFFLINE"
            ? Object.fromEntries(
                Object.entries(state.typingByRoom).map(([roomId, users]) => {
                  if (!users[userId]) {
                    return [roomId, users]
                  }

                  const nextUsers = { ...users }
                  delete nextUsers[userId]

                  if (Object.keys(nextUsers).length === 0) {
                    return [roomId, undefined]
                  }

                  return [roomId, nextUsers]
                }).filter((entry): entry is [string, Record<string, true>] => Boolean(entry[1]))
              )
            : state.typingByRoom,
      }
    }),

  clearAllOnline: () => {
    typingTimers.forEach((timer) => clearTimeout(timer))
    typingTimers.clear()

    const currentStatuses = get().userStatuses
    const selfPresence = get().selfPresence
    const myId = localStorage.getItem("my_user_id")
    const userStatuses: Record<UserId, PresenceStatus> =
      myId
        ? {
            [myId]: currentStatuses[myId] ?? selfPresence?.effectiveStatus ?? "OFFLINE",
          }
        : {}

    set({
      onlineUsers: buildVisibleUserMap(userStatuses),
      onlineUsersByRoom: {},
      typingByRoom: {},
      userStatuses,
      roomUserStatuses: {},
    })
  },

  getUserStatus: (userId) => {
    return get().userStatuses[userId] ?? "OFFLINE"
  },

  isUserOnline: (userId) => get().getUserStatus(userId) === "ONLINE",

  setRoomPresence: (roomId, users) =>
    set((state) => {
      const myId = localStorage.getItem("my_user_id")
      const roomStatuses: Record<UserId, PresenceStatus> = {}
      users.forEach((user) => {
        roomStatuses[user.userId] =
          myId && user.userId === myId
            ? state.userStatuses[user.userId] ?? user.status
            : user.status
      })

      return {
        roomUserStatuses: {
          ...state.roomUserStatuses,
          [roomId]: roomStatuses,
        },
        onlineUsersByRoom: {
          ...state.onlineUsersByRoom,
          [roomId]: buildVisibleUserMap(roomStatuses),
        },
      }
    }),

  clearRoomOnline: (roomId) =>
    set((state) => {
      const nextOnline = { ...state.onlineUsersByRoom }
      const nextStatuses = { ...state.roomUserStatuses }

      delete nextOnline[roomId]
      delete nextStatuses[roomId]

      return {
        onlineUsersByRoom: nextOnline,
        roomUserStatuses: nextStatuses,
      }
    }),

  getOnlineUsersInRoom: (roomId) => Object.keys(get().onlineUsersByRoom[roomId] ?? {}),

  getRoomUserStatus: (roomId, userId) => {
    return get().roomUserStatuses[roomId]?.[userId] ?? get().getUserStatus(userId)
  },

  setUserTyping: (roomId, userId) => {
    const currentUserId = localStorage.getItem("my_user_id")
    if (!roomId || !userId || userId === currentUserId) {
      return
    }

    const key = typingTimerKey(roomId, userId)
    const existingTimer = typingTimers.get(key)
    if (existingTimer) {
      clearTimeout(existingTimer)
    }

    const timeout = window.setTimeout(() => {
      typingTimers.delete(key)
      get().setUserStopTyping(roomId, userId)
    }, TYPING_TIMEOUT_MS)

    typingTimers.set(key, timeout)

    set((state) => ({
      typingByRoom: {
        ...state.typingByRoom,
        [roomId]: {
          ...(state.typingByRoom[roomId] ?? {}),
          [userId]: true,
        },
      },
    }))
  },

  setUserStopTyping: (roomId, userId) => {
    const key = typingTimerKey(roomId, userId)
    const timer = typingTimers.get(key)
    if (timer) {
      clearTimeout(timer)
      typingTimers.delete(key)
    }

    set((state) => {
      const roomTyping = state.typingByRoom[roomId]
      if (!roomTyping || !roomTyping[userId]) return state

      const nextRoomTyping = { ...roomTyping }
      delete nextRoomTyping[userId]

      const nextTypingByRoom = { ...state.typingByRoom }
      if (Object.keys(nextRoomTyping).length === 0) {
        delete nextTypingByRoom[roomId]
      } else {
        nextTypingByRoom[roomId] = nextRoomTyping
      }

      return { typingByRoom: nextTypingByRoom }
    })
  },

  clearRoomTyping: (roomId) => {
    Array.from(typingTimers.keys())
      .filter((key) => key.startsWith(`${roomId}:`))
      .forEach((key) => {
        const timer = typingTimers.get(key)
        if (timer) {
          clearTimeout(timer)
        }
        typingTimers.delete(key)
      })

    set((state) => {
      if (!state.typingByRoom[roomId]) return state

      const nextTypingByRoom = { ...state.typingByRoom }
      delete nextTypingByRoom[roomId]

      return { typingByRoom: nextTypingByRoom }
    })
  },

  clearUserTypingEverywhere: (userId) => {
    Array.from(typingTimers.keys())
      .filter((key) => key.endsWith(`:${userId}`))
      .forEach((key) => {
        const timer = typingTimers.get(key)
        if (timer) {
          clearTimeout(timer)
        }
        typingTimers.delete(key)
      })

    set((state) => ({
      typingByRoom: Object.fromEntries(
        Object.entries(state.typingByRoom)
          .map(([roomId, users]) => {
            if (!users[userId]) {
              return [roomId, users]
            }

            const nextUsers = { ...users }
            delete nextUsers[userId]

            if (Object.keys(nextUsers).length === 0) {
              return [roomId, undefined]
            }

            return [roomId, nextUsers]
          })
          .filter((entry): entry is [string, Record<string, true>] => Boolean(entry[1]))
      ),
    }))
  },

  getTypingUsers: (roomId) => Object.keys(get().typingByRoom[roomId] ?? {}),
}))
