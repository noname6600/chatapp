import type { Room } from "../types/room"
import type { UserProfile } from "../types/user"

type RoomActivityLike = Room & {
  updatedAt?: string | null
}

const deriveLatestMessageAt = (room: Room): string | null => {
  if (room.latestMessageAt) return room.latestMessageAt
  if (room.lastMessage?.createdAt) return room.lastMessage.createdAt
  return null
}

type ResolveSenderNameInput = {
  room: Room
  senderId: string
  currentUserId: string | null
  usersById?: Record<string, UserProfile>
}

const parseTimestampMs = (value?: string | null): number => {
  if (!value) return 0

  const ts = Date.parse(value)
  return Number.isFinite(ts) ? ts : 0
}

export const resolveLastMessageSenderName = ({
  room,
  senderId,
  currentUserId,
  usersById,
}: ResolveSenderNameInput): string => {
  // If the sender is the current user
  if (senderId && currentUserId && senderId === currentUserId) {
    return "You"
  }

  // Try to look up the user by senderId in the user cache
  if (usersById && usersById[senderId]) {
    return usersById[senderId].displayName || usersById[senderId].username || "Unknown"
  }

  // For private rooms, fall back to room.name (which is typically the other user's name)
  if (room.type === "PRIVATE") {
    const fallbackName = room.name?.trim() ?? ""
    if (fallbackName) return fallbackName
  }

  return "Unknown"
}

export const normalizeRoomForList = (
  room: Room,
  currentUserId: string | null,
  usersById?: Record<string, UserProfile>
): Room => {
  const latestMessageAt = deriveLatestMessageAt(room)

  if (!room.lastMessage) {
    if (room.latestMessageAt === latestMessageAt) return room
    return {
      ...room,
      latestMessageAt,
    }
  }

  const senderName = resolveLastMessageSenderName({
    room,
    senderId: room.lastMessage.senderId,
    currentUserId,
    usersById,
  })

  if (senderName === room.lastMessage.senderName && room.latestMessageAt === latestMessageAt) {
    return room
  }

  return {
    ...room,
    latestMessageAt,
    lastMessage: {
      ...room.lastMessage,
      senderName,
    },
  }
}

export const getRoomLatestActivityMs = (room: Room): number => {
  const activityRoom = room as RoomActivityLike

  return (
    parseTimestampMs(deriveLatestMessageAt(room)) ||
    parseTimestampMs(activityRoom.updatedAt) ||
    parseTimestampMs(room.createdAt)
  )
}

const getTieBreakerMs = (room: Room): number => {
  const activityRoom = room as RoomActivityLike

  return parseTimestampMs(activityRoom.updatedAt) || parseTimestampMs(room.createdAt)
}

export const compareRoomsByRecency = (a: Room, b: Room): number => {
  const latestDiff = getRoomLatestActivityMs(b) - getRoomLatestActivityMs(a)
  if (latestDiff !== 0) return latestDiff

  const tieDiff = getTieBreakerMs(b) - getTieBreakerMs(a)
  if (tieDiff !== 0) return tieDiff

  return a.id.localeCompare(b.id)
}

export const sortRoomIdsByRecency = (roomsById: Record<string, Room>, roomIds?: string[]): string[] => {
  const rooms = (roomIds ?? Object.keys(roomsById))
    .map((roomId) => roomsById[roomId])
    .filter((room): room is Room => Boolean(room))

  return rooms
    .sort(compareRoomsByRecency)
    .map((room) => room.id)
}

export const getSplitRoomSections = (
  roomsById: Record<string, Room>,
  roomOrder: string[]
): { groupRoomIds: string[]; privateRoomIds: string[] } => {
  const groupRoomIds: string[] = []
  const privateRoomIds: string[] = []

  roomOrder.forEach((roomId) => {
    const room = roomsById[roomId]
    if (!room) return

    if (room.type === "GROUP") {
      groupRoomIds.push(roomId)
      return
    }

    if (room.type === "PRIVATE") {
      privateRoomIds.push(roomId)
    }
  })

  return {
    groupRoomIds,
    privateRoomIds: sortRoomIdsByRecency(roomsById, privateRoomIds),
  }
}
