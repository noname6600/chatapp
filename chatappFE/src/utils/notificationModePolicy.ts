import type { Notification, RoomNotificationMode } from "../types/notification"

export const normalizeRoomNotificationMode = (
  mode?: RoomNotificationMode | null,
  legacyIsMuted?: boolean
): RoomNotificationMode => {
  if (mode === "NO_RESTRICT" || mode === "ONLY_MENTION" || mode === "NOTHING") {
    return mode
  }

  return legacyIsMuted ? "NOTHING" : "NO_RESTRICT"
}

export const getModeFromStore = (
  roomId: string | null | undefined,
  modesByRoom: Record<string, RoomNotificationMode>
): RoomNotificationMode => {
  if (!roomId) return "NO_RESTRICT"
  return modesByRoom[roomId] ?? "NO_RESTRICT"
}

export const shouldDeliverRoomEventByMode = (
  mode: RoomNotificationMode,
  isMentioned: boolean
): boolean => {
  if (mode === "NOTHING") return false
  if (mode === "ONLY_MENTION") return isMentioned
  return true
}

export const shouldCountNotificationAsUnreadByMode = (
  notification: Notification,
  modesByRoom: Record<string, RoomNotificationMode>
): boolean => {
  const isRoomNotification =
    notification.type === "MESSAGE" || notification.type === "MENTION"

  if (!isRoomNotification || !notification.roomId) {
    return true
  }

  const mode = getModeFromStore(notification.roomId, modesByRoom)
  const isMentioned = notification.type === "MENTION"
  return shouldDeliverRoomEventByMode(mode, isMentioned)
}
