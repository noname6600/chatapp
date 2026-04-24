export type NotificationType =
  | "MESSAGE"
  | "MENTION"
  | "REPLY"
  | "REACTION"
  | "FRIEND_REQUEST"
  | "FRIEND_REQUEST_ACCEPTED"
  | "GROUP_INVITE"

export type RoomNotificationMode =
  | "NO_RESTRICT"
  | "ONLY_MENTION"
  | "NOTHING"

export interface Notification {
  id: string
  type: NotificationType
  referenceId: string | null
  roomId: string | null
  senderId?: string | null
  actorId?: string | null
  actorDisplayName?: string | null
  senderName: string | null
  preview: string | null
  isRead: boolean
  actionRequired?: boolean
  createdAt: string | null
}

export interface NotificationListResponse {
  notifications: Notification[]
  unreadCount: number
  page?: number
  size?: number
  hasMore?: boolean
  nextPage?: number | null
  windowCreatedAt?: string | null
}

export interface NotificationListParams {
  page?: number
  size?: number
  beforeCreatedAt?: string
}

export interface RoomSettingsResponse {
  mode?: RoomNotificationMode | null
  isMuted?: boolean
}

export interface UnreadCountResponse {
  unreadCount: number
}
