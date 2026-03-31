export type NotificationType =
  | "MESSAGE"
  | "MENTION"
  | "FRIEND_REQUEST"
  | "FRIEND_REQUEST_ACCEPTED"

export interface Notification {
  id: string
  type: NotificationType
  referenceId: string | null
  roomId: string | null
  senderId?: string | null
  senderName: string | null
  preview: string | null
  isRead: boolean
  createdAt: string
}

export interface NotificationListResponse {
  notifications: Notification[]
  unreadCount: number
}

export interface RoomSettingsResponse {
  isMuted: boolean
}

export interface UnreadCountResponse {
  unreadCount: number
}