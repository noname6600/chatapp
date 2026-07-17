export const NotificationEventType = {
  NOTIFICATION_NEW: "notification.new",
  UNREAD_COUNT_UPDATE: "notification.unread.count.updated",
} as const

export type NotificationEventTypeValue =
  (typeof NotificationEventType)[keyof typeof NotificationEventType]