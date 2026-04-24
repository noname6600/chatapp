import {
  AtSign,
  Check,
  Users,
  MessageCircle,
  UserPlus,
  SmilePlus,
  AlertCircle,
} from "lucide-react"
import { useCallback, useEffect, useRef } from "react"
import type { CSSProperties } from "react"

import { useRooms } from "../../store/room.store"
import { useUserStore } from "../../store/user.store"
import type { Notification } from "../../types/notification"

interface NotificationPanelProps {
  notifications: Notification[]
  unreadCount: number
  unreadFriendRequests?: number
  onMarkAllRead: () => Promise<void>
  onNotificationClick: (notification: Notification) => Promise<void>
  hasMore?: boolean
  isLoadingMore?: boolean
  onLoadMore?: () => Promise<void>
  panelClassName?: string
  panelStyle?: CSSProperties
}

const LOAD_MORE_THRESHOLD_PX = 120
const LOAD_MORE_DEBOUNCE_MS = 250

const getTypeIcon = (type: Notification["type"]) => {
  if (type === "MENTION") return <AtSign size={16} className="text-indigo-600" />
  if (type === "MESSAGE") return <MessageCircle size={16} className="text-blue-600" />
  if (type === "REPLY") return <MessageCircle size={16} className="text-sky-600" />
  if (type === "REACTION") return <SmilePlus size={16} className="text-yellow-500" />
  if (type === "FRIEND_REQUEST") return <UserPlus size={16} className="text-emerald-600" />
  if (type === "GROUP_INVITE") return <Users size={16} className="text-orange-600" />
  return <Check size={16} className="text-emerald-600" />
}

const parseDate = (createdAt: string | null) => {
  if (!createdAt || createdAt.trim().length === 0) return null

  const asNumber = Number(createdAt)
  if (!Number.isNaN(asNumber) && Number.isFinite(asNumber)) {
    const ms = asNumber < 1_000_000_000_000 ? asNumber * 1000 : asNumber
    const numericDate = new Date(ms)
    return Number.isNaN(numericDate.getTime()) ? null : numericDate
  }

  const value = new Date(createdAt)
  return Number.isNaN(value.getTime()) ? null : value
}

const formatTimestamp = (createdAt: string | null) => {
  const value = parseDate(createdAt)
  if (!value) return ""

  if (Number.isNaN(value.getTime())) return ""
  return value.toLocaleString()
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const isRoomNotification = (notification: Notification) => {
  return (
    notification.type === "MESSAGE" ||
    notification.type === "MENTION" ||
    notification.type === "REPLY" ||
    notification.type === "REACTION" ||
    notification.type === "GROUP_INVITE"
  )
}

const formatSenderLabel = (notification: Notification, usersById: Record<string, { displayName?: string | null }>) => {
  const actorDisplayName = notification.actorDisplayName?.trim()
  if (actorDisplayName) return actorDisplayName

  if (notification.actorId && usersById[notification.actorId]?.displayName) {
    const cached = String(usersById[notification.actorId]?.displayName ?? "").trim()
    if (cached) return cached
  }

  const senderName = notification.senderName?.trim()
  if (senderName && !UUID_PATTERN.test(senderName)) return senderName

  return "Someone"
}

const getNotificationTitle = (
  notification: Notification,
  roomsById: Record<string, { name: string; type?: string }>,
  usersById: Record<string, { displayName?: string | null }>
) => {
  if (notification.type === "MESSAGE" && notification.roomId) {
    const room = roomsById[notification.roomId]
    if (room?.type === "PRIVATE") {
      return formatSenderLabel(notification, usersById)
    }
    return room?.name || "Room"
  }

  if (isRoomNotification(notification) && notification.roomId) {
    return roomsById[notification.roomId]?.name || "Room"
  }

  return formatSenderLabel(notification, usersById)
}

const buildPreviewParts = (
  notification: Notification,
  usersById: Record<string, { displayName?: string | null }>,
  roomsById: Record<string, { name: string; type?: string }>
) => {
  const sender = formatSenderLabel(notification, usersById)

  if (notification.type === "MENTION") return { actor: sender, action: "mentioned you" }
  if (notification.type === "REACTION") return { actor: sender, action: "reacted to your message" }
  if (notification.type === "REPLY") return { actor: sender, action: "replied to your message" }
  if (notification.type === "FRIEND_REQUEST") return { actor: sender, action: "sent you a friend request" }
  if (notification.type === "FRIEND_REQUEST_ACCEPTED") return { actor: sender, action: "accepted your friend request" }
  if (notification.type === "GROUP_INVITE") return { actor: sender, action: "invited you to a group" }

  if (notification.type === "MESSAGE") {
    const room = notification.roomId ? roomsById[notification.roomId] : undefined
    if (room?.type === "PRIVATE") {
      return { actor: sender, action: "sent you a message" }
    }

    if (notification.preview && notification.preview.trim().length > 0) {
      return { action: notification.preview }
    }

    return { action: "New message" }
  }

  if (notification.preview && notification.preview.trim().length > 0) {
    return { action: notification.preview }
  }

  return { action: "Notification" }
}

const NotificationPanel = ({
  notifications,
  unreadCount,
  unreadFriendRequests = 0,
  onMarkAllRead,
  onNotificationClick,
  hasMore = false,
  isLoadingMore = false,
  onLoadMore,
  panelClassName,
  panelStyle,
}: NotificationPanelProps) => {
  const { roomsById } = useRooms()
  const users = useUserStore((state) => state.users)
  const fetchUsers = useUserStore((state) => state.fetchUsers)
  const listViewportRef = useRef<HTMLDivElement | null>(null)
  const lastLoadAttemptRef = useRef(0)

  const maybeLoadMore = useCallback(async () => {
    if (!onLoadMore || !hasMore || isLoadingMore) {
      return
    }

    const now = Date.now()
    if (now - lastLoadAttemptRef.current < LOAD_MORE_DEBOUNCE_MS) {
      return
    }
    lastLoadAttemptRef.current = now

    await onLoadMore()
  }, [hasMore, isLoadingMore, onLoadMore])

  const handleViewportScroll = useCallback(() => {
    const viewport = listViewportRef.current
    if (!viewport) {
      return
    }

    const remaining = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight
    if (remaining <= LOAD_MORE_THRESHOLD_PX) {
      void maybeLoadMore()
    }
  }, [maybeLoadMore])

  useEffect(() => {
    const ids: string[] = []
    for (const notification of notifications) {
      if (
        notification.actorId &&
        !notification.actorDisplayName &&
        (!notification.senderName || UUID_PATTERN.test(notification.senderName))
      ) {
        ids.push(notification.actorId)
      }

      if (
        (notification.type === "MENTION" ||
          notification.type === "REACTION" ||
          notification.type === "REPLY" ||
          notification.type === "FRIEND_REQUEST" ||
          notification.type === "FRIEND_REQUEST_ACCEPTED" ||
          notification.type === "GROUP_INVITE") &&
        !notification.actorId &&
        !notification.actorDisplayName &&
        !notification.senderName
      ) {
        // Missing actor metadata is tolerated and rendered via safe fallbacks.
      }
    }

    void fetchUsers(ids)
  }, [fetchUsers, notifications])

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
          <p className="text-xs text-gray-500">Unread notifications: {unreadCount}</p>
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

      {unreadFriendRequests > 0 && (
        <div className="mx-3 mt-3 rounded-lg border border-amber-200 bg-gradient-to-r from-amber-50 to-orange-50 px-3 py-2.5">
          <div className="flex items-start gap-2">
            <AlertCircle size={16} className="mt-0.5 shrink-0 text-amber-700" />
            <div className="min-w-0 flex-1">
              <p className="text-xs font-bold uppercase tracking-wide text-amber-800">Action needed</p>
              <p className="mt-0.5 text-xs font-medium text-amber-900">
                Pending friend requests: {unreadFriendRequests}
              </p>
            </div>
          </div>
        </div>
      )}

      <div
        ref={listViewportRef}
        className="max-h-96 overflow-y-auto py-1"
        onScroll={handleViewportScroll}
        data-testid="notification-panel-scroll-viewport"
      >
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
              className={`group w-full border-b px-4 py-3 text-left last:border-b-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-400 ${
                notification.isRead
                  ? "bg-white/80 opacity-70 transition hover:bg-slate-50 hover:opacity-100"
                  : "border-l-4 border-l-blue-500 bg-blue-50/90 shadow-[inset_0_0_0_1px_rgba(59,130,246,0.14)] transition hover:bg-blue-100/80"
              }`}
            >
              <div className="flex items-start gap-2">
                <span className="mt-1 shrink-0">{getTypeIcon(notification.type)}</span>

                <div className="min-w-0 flex-1">
                  <p className={`truncate text-gray-900 ${isRoomNotification(notification) ? "text-base font-semibold" : "text-sm font-semibold"}`}>
                    {getNotificationTitle(notification, roomsById as Record<string, { name: string; type?: string }>, users)}
                  </p>
                  <p className={`line-clamp-2 text-sm ${notification.isRead ? "text-gray-500 group-hover:text-gray-700" : "text-gray-800"}`}>
                    {(() => {
                      const previewParts = buildPreviewParts(notification, users, roomsById as Record<string, { name: string; type?: string }>)
                      if (!previewParts.actor) {
                        return previewParts.action
                      }

                      return (
                        <>
                          <span className={notification.isRead ? "font-semibold text-gray-700 group-hover:text-gray-900" : "font-bold text-gray-900"}>
                            {previewParts.actor}
                          </span>{" "}
                          <span>{previewParts.action}</span>
                        </>
                      )
                    })()}
                  </p>
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

        {isLoadingMore && notifications.length > 0 && (
          <p className="px-4 py-2 text-center text-xs text-gray-500">Loading older notifications...</p>
        )}
      </div>
    </div>
  )
}

export default NotificationPanel
