import { useEffect, useMemo, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { Bell } from "lucide-react"
import { useNavigate } from "react-router-dom"

import { useNotifications } from "../../store/notification.store"
import { useChat } from "../../store/chat.store"
import { useFriendStore } from "../../store/friend.store"
import type { Notification } from "../../types/notification"
import NotificationPanel from "./NotificationPanel"

interface NotificationBellProps {
  compact?: boolean
}

const isRoomNotification = (notification: Notification) => {
  return (
    notification.type === "MESSAGE" ||
    notification.type === "MENTION" ||
    notification.type === "REPLY" ||
    notification.type === "REACTION" ||
    notification.type === "GROUP_INVITE"
  )
}

const NotificationBell = ({ compact = false }: NotificationBellProps) => {
  const navigate = useNavigate()
  const { setActiveRoom } = useChat()

  const {
    notifications,
    unreadCount,
    hasMoreNotifications,
    isLoadingMoreNotifications,
    fetchNotifications,
    loadMoreNotifications,
    markRead,
    markAllRead,
  } = useNotifications()
  const unreadFriendRequests = useFriendStore((s) => s.unreadFriendRequestCount)

  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const panelRef = useRef<HTMLDivElement | null>(null)
  const triggerRef = useRef<HTMLButtonElement | null>(null)
  const [panelPosition, setPanelPosition] = useState<{ top: number; left: number } | null>(null)

  const PANEL_WIDTH = 352
  const PANEL_HEIGHT = 420

  const updatePanelPosition = () => {
    const trigger = triggerRef.current
    if (!trigger) return

    const rect = trigger.getBoundingClientRect()

    let left = compact ? rect.right + 8 : rect.right - PANEL_WIDTH
    left = Math.max(8, Math.min(left, window.innerWidth - PANEL_WIDTH - 8))

    let top = rect.bottom + 8
    if (top + PANEL_HEIGHT > window.innerHeight) {
      top = Math.max(8, rect.top - PANEL_HEIGHT - 8)
    }

    setPanelPosition({ top, left })
  }

  useEffect(() => {
    void fetchNotifications().catch((error) => {
      console.error("Failed to fetch notifications:", error)
    })
  }, [fetchNotifications])

  useEffect(() => {
    if (!isOpen) return

    updatePanelPosition()

    const onPointerDown = (event: MouseEvent) => {
      const targetNode = event.target as Node
      const clickedTriggerContainer = containerRef.current?.contains(targetNode)
      const clickedPanel = panelRef.current?.contains(targetNode)

      if (!clickedTriggerContainer && !clickedPanel) {
        setIsOpen(false)
      }
    }

    const onViewportChange = () => {
      updatePanelPosition()
    }

    document.addEventListener("mousedown", onPointerDown)
    window.addEventListener("resize", onViewportChange)
    window.addEventListener("scroll", onViewportChange, true)

    return () => {
      document.removeEventListener("mousedown", onPointerDown)
      window.removeEventListener("resize", onViewportChange)
      window.removeEventListener("scroll", onViewportChange, true)
    }
  }, [isOpen])

  const badgeText = useMemo(() => {
    // unreadCount from the notification store already includes unresolved
    // friend-request notifications, so do not add unreadFriendRequests again.
    if (unreadCount <= 0) return ""
    if (unreadCount > 99) return "99+"
    return String(unreadCount)
  }, [unreadCount])

  const handleMarkAllRead = async () => {
    await markAllRead()
  }

  const handleNotificationClick = async (notification: Notification) => {
    if (!notification.isRead && !notification.actionRequired && notification.type !== "FRIEND_REQUEST") {
      await markRead(notification.id)
    }

    if (isRoomNotification(notification) && notification.roomId) {
      void setActiveRoom(notification.roomId)
      navigate("/chat")
      setIsOpen(false)
      return
    }

    if (notification.type === "FRIEND_REQUEST" || notification.type === "FRIEND_REQUEST_ACCEPTED") {
      navigate("/friends?tab=pending")
      setIsOpen(false)
      return
    }

    navigate("/notifications")
    setIsOpen(false)
  }

  return (
    <div ref={containerRef} className={`relative z-[120] ${compact ? "flex justify-center" : ""}`}>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-full border border-gray-200 bg-white text-gray-700 hover:bg-gray-50"
        aria-label="Open notifications"
      >
        <Bell size={18} />
        {badgeText && (
          <span
            className="absolute -right-1 -top-1 flex flex-col items-end gap-1"
            data-testid="notification-badge-anchor"
          >
            <span
              data-testid="notification-badge"
              className="min-w-5 rounded-full bg-red-500 px-1.5 py-0.5 text-center text-[10px] font-semibold text-white"
            >
              {badgeText}
            </span>
          </span>
        )}
      </button>

      {isOpen && (
        createPortal(
          <div className="fixed inset-0 z-[130]">
            <div ref={panelRef} className="absolute">
              <NotificationPanel
                notifications={notifications}
                unreadCount={unreadCount}
                unreadFriendRequests={unreadFriendRequests}
                onMarkAllRead={handleMarkAllRead}
                onNotificationClick={handleNotificationClick}
                hasMore={hasMoreNotifications}
                isLoadingMore={isLoadingMoreNotifications}
                onLoadMore={loadMoreNotifications}
                panelClassName="absolute z-[130]"
                panelStyle={panelPosition ? { top: panelPosition.top, left: panelPosition.left } : undefined}
              />
            </div>
          </div>,
          document.body
        )
      )}
    </div>
  )
}

export default NotificationBell
