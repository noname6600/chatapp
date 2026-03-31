import {
  AtSign,
  Check,
  MessageCircle,
  UserPlus,
} from "lucide-react"
import type { CSSProperties } from "react"

import { useRooms } from "../../store/room.store"
import type { Notification } from "../../types/notification"

interface NotificationPanelProps {
  notifications: Notification[]
  unreadCount: number
  onMarkAllRead: () => Promise<void>
  onNotificationClick: (notification: Notification) => Promise<void>
  panelClassName?: string
  panelStyle?: CSSProperties
}

const getTypeIcon = (type: Notification["type"]) => {
  if (type === "MENTION") return <AtSign size={16} className="text-indigo-600" />
  if (type === "MESSAGE") return <MessageCircle size={16} className="text-blue-600" />
  if (type === "FRIEND_REQUEST") return <UserPlus size={16} className="text-emerald-600" />
  return <Check size={16} className="text-emerald-600" />
}

const formatTimestamp = (createdAt: string) => {
  const value = new Date(createdAt)
  if (Number.isNaN(value.getTime())) return ""
  return value.toLocaleString()
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const isRoomNotification = (notification: Notification) => {
  return notification.type === "MESSAGE" || notification.type === "MENTION"
}

const formatSenderLabel = (senderName: string | null) => {
  if (!senderName || senderName.trim().length === 0) return "Someone"
  if (UUID_PATTERN.test(senderName.trim())) return "Someone"
  return senderName.trim()
}

const getNotificationTitle = (
  notification: Notification,
  roomsById: Record<string, { name: string }>
) => {
  if (isRoomNotification(notification) && notification.roomId) {
    return roomsById[notification.roomId]?.name || "Room"
  }

  return formatSenderLabel(notification.senderName)
}

const buildPreview = (notification: Notification) => {
  const sender = formatSenderLabel(notification.senderName)

  if (notification.type === "MENTION") {
    return `${sender} mentioned you`
  }

  if (notification.preview && notification.preview.trim().length > 0) {
    return notification.preview
  }

  if (notification.type === "MESSAGE") return "New message"
  if (notification.type === "FRIEND_REQUEST") return "New friend request"
  return "Friend request accepted"
}

const NotificationPanel = ({
  notifications,
  unreadCount,
  onMarkAllRead,
  onNotificationClick,
  panelClassName,
  panelStyle,
}: NotificationPanelProps) => {
  const { roomsById } = useRooms()

  return (
    <div
      style={panelStyle}
      className={`w-[22rem] max-w-[90vw] rounded-xl border border-gray-200 bg-white shadow-xl z-[130] ${
        panelClassName || "absolute right-0 mt-2"
      }`}
    >
      <div className="flex items-center justify-between border-b px-4 py-3">
        <div>
          <p className="text-sm font-semibold text-gray-900">Notifications</p>
          <p className="text-xs text-gray-500">Unread: {unreadCount}</p>
        </div>

        <button
          type="button"
          onClick={() => {
            void onMarkAllRead()
          }}
          className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          disabled={unreadCount === 0}
        >
          Mark all read
        </button>
      </div>

      <div className="max-h-96 overflow-y-auto py-1">
        {notifications.length === 0 ? (
          <p className="px-4 py-6 text-center text-sm text-gray-500">No notifications yet</p>
        ) : (
          notifications.map((notification) => (
            <button
              key={notification.id}
              type="button"
              onClick={() => {
                void onNotificationClick(notification)
              }}
              className={`w-full border-b px-4 py-3 text-left last:border-b-0 hover:bg-gray-50 ${
                notification.isRead ? "bg-white" : "bg-blue-50/60"
              }`}
            >
              <div className="flex items-start gap-2">
                <span className="mt-1 shrink-0">{getTypeIcon(notification.type)}</span>

                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-gray-900">
                    {getNotificationTitle(notification, roomsById as Record<string, { name: string }>)}
                  </p>
                  <p className="line-clamp-2 text-sm text-gray-700">{buildPreview(notification)}</p>
                  <p className="mt-1 text-xs text-gray-500">{formatTimestamp(notification.createdAt)}</p>
                </div>

                {!notification.isRead && (
                  <span
                    aria-label="Unread notification"
                    className="mt-2 h-2.5 w-2.5 shrink-0 rounded-full bg-blue-500"
                  />
                )}
              </div>
            </button>
          ))
        )}
      </div>
    </div>
  )
}

export default NotificationPanel