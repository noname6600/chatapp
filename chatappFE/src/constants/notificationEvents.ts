export const NotificationEventType = {
  NOTIFICATION_NEW: "NOTIFICATION_NEW",
  UNREAD_COUNT_UPDATE: "UNREAD_COUNT_UPDATE",
} as const

export type NotificationEventTypeValue =
  (typeof NotificationEventType)[keyof typeof NotificationEventType]