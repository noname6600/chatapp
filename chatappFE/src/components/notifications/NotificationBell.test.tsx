/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import type { CSSProperties } from "react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import NotificationBell from "./NotificationBell"

const mocks = vi.hoisted(() => ({
  fetchNotifications: vi.fn(async () => {}),
  loadMoreNotifications: vi.fn(async () => {}),
  markRead: vi.fn(async () => {}),
  markAllRead: vi.fn(async () => {}),
  setActiveRoom: vi.fn(async () => {}),
  navigate: vi.fn(),
  unreadCount: 1,
  unreadFriendRequests: 0,
  notifications: [
    {
      id: "n-1",
      type: "MESSAGE",
      referenceId: "m-1",
      roomId: "room-1",
      senderName: "Alice",
      preview: "Hi",
      isRead: false,
      createdAt: "2026-03-27T10:00:00.000Z",
    },
  ],
}))

vi.mock("react-router-dom", () => ({
  useNavigate: () => mocks.navigate,
}))

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    setActiveRoom: mocks.setActiveRoom,
  }),
}))

vi.mock("../../store/friend.store", () => ({
  useFriendStore: () => mocks.unreadFriendRequests,
}))

vi.mock("../../store/notification.store", () => ({
  useNotifications: () => ({
    notifications: mocks.notifications,
    unreadCount: mocks.unreadCount,
    hasMoreNotifications: false,
    isLoadingMoreNotifications: false,
    fetchNotifications: mocks.fetchNotifications,
    loadMoreNotifications: mocks.loadMoreNotifications,
    markRead: mocks.markRead,
    markAllRead: mocks.markAllRead,
  }),
}))

vi.mock("./NotificationPanel", () => ({
  default: ({
    notifications,
    unreadCount,
    unreadFriendRequests,
    onNotificationClick,
    panelClassName,
    panelStyle,
  }: {
    notifications: Array<{ id: string; type: string; roomId: string | null }>
    unreadCount: number
    unreadFriendRequests?: number
    onNotificationClick: (notification: { id: string; type: string; roomId: string | null; isRead: boolean }) => Promise<void>
    hasMore?: boolean
    isLoadingMore?: boolean
    onLoadMore?: () => Promise<void>
    panelClassName?: string
    panelStyle?: CSSProperties
  }) => (
    <div data-testid="notification-panel" className={panelClassName} style={panelStyle}>
      <div data-testid="notification-unread">{unreadCount}</div>
      <div data-testid="notification-friend-unread">{unreadFriendRequests ?? 0}</div>
      <button
        type="button"
        onClick={() => {
          void onNotificationClick({ ...notifications[0], isRead: false })
        }}
      >
        Open first notification
      </button>
    </div>
  ),
}))

describe("NotificationBell overlay behavior", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.unreadCount = 1
    mocks.unreadFriendRequests = 0
    mocks.notifications = [
      {
        id: "n-1",
        type: "MESSAGE",
        referenceId: "m-1",
        roomId: "room-1",
        senderName: "Alice",
        preview: "Hi",
        isRead: false,
        createdAt: "2026-03-27T10:00:00.000Z",
      },
    ]
  })

  afterEach(() => {
    cleanup()
  })

  it("renders notification panel into body overlay, outside sidebar container", async () => {
    const { container } = render(
      <div data-testid="sidebar" className="relative z-[110] overflow-hidden">
        <NotificationBell />
      </div>
    )

    fireEvent.click(screen.getByRole("button", { name: "Open notifications" }))

    const panel = await screen.findByTestId("notification-panel")
    expect(document.body.contains(panel)).toBe(true)
    expect(container.contains(panel)).toBe(false)
  })

  it("keeps panel interactive when sidebar is visible and routes room notifications", async () => {
    render(
      <div className="grid grid-cols-[20rem_1fr]">
        <div data-testid="room-list">Room list</div>
        <NotificationBell />
      </div>
    )

    fireEvent.click(screen.getAllByRole("button", { name: "Open notifications" })[0])
    fireEvent.click(await screen.findByRole("button", { name: "Open first notification" }))

    await waitFor(() => {
      expect(mocks.setActiveRoom).toHaveBeenCalledWith("room-1")
      expect(mocks.navigate).toHaveBeenCalledWith("/chat")
      expect(mocks.markRead).toHaveBeenCalledWith("n-1")
    })
  })

  it("routes REACTION notifications to the target room", async () => {
    mocks.notifications = [
      {
        id: "n-reaction-1",
        type: "REACTION",
        referenceId: "m-1",
        roomId: "room-reaction",
        senderName: "Alice",
        preview: "Reacted",
        isRead: false,
        createdAt: "2026-03-27T10:00:00.000Z",
      },
    ]

    render(<NotificationBell />)

    fireEvent.click(screen.getByRole("button", { name: "Open notifications" }))
    fireEvent.click(await screen.findByRole("button", { name: "Open first notification" }))

    await waitFor(() => {
      expect(mocks.setActiveRoom).toHaveBeenCalledWith("room-reaction")
      expect(mocks.navigate).toHaveBeenCalledWith("/chat")
    })
  })

  it("routes GROUP_INVITE notifications to the target room", async () => {
    mocks.notifications = [
      {
        id: "n-invite-1",
        type: "GROUP_INVITE",
        referenceId: "room-2",
        roomId: "room-invite",
        senderName: "Bob",
        preview: "Invite",
        isRead: false,
        createdAt: "2026-03-27T10:00:00.000Z",
      },
    ]

    render(<NotificationBell />)

    fireEvent.click(screen.getByRole("button", { name: "Open notifications" }))
    fireEvent.click(await screen.findByRole("button", { name: "Open first notification" }))

    await waitFor(() => {
      expect(mocks.setActiveRoom).toHaveBeenCalledWith("room-invite")
      expect(mocks.navigate).toHaveBeenCalledWith("/chat")
    })
  })

  it("passes unresolved friend-request unread to panel for highlighting", async () => {
    mocks.unreadFriendRequests = 3

    render(<NotificationBell />)

    fireEvent.click(screen.getByRole("button", { name: "Open notifications" }))

    const friendUnreadNode = await screen.findByTestId("notification-friend-unread")
    expect(friendUnreadNode.textContent).toBe("3")
  })

  it("renders a single unread badge from store unreadCount", () => {
    mocks.unreadFriendRequests = 3

    render(<NotificationBell />)

    expect(screen.getByTestId("notification-badge").textContent).toBe("1")
  })

  it("does not render a badge when store unreadCount is zero", () => {
    mocks.unreadCount = 0
    mocks.unreadFriendRequests = 2

    render(<NotificationBell />)

    expect(screen.queryByTestId("notification-badge")).toBeNull()
  })

  it("anchors all bell badges to the canonical top-right zone", () => {
    mocks.unreadCount = 2
    mocks.unreadFriendRequests = 3

    render(<NotificationBell />)

    const anchor = screen.getByTestId("notification-badge-anchor")
    expect(anchor.className.includes("-right-1")).toBe(true)
    expect(anchor.className.includes("-top-1")).toBe(true)
    expect(anchor.className.includes("-left")).toBe(false)
    expect(anchor.className.includes("-bottom")).toBe(false)
  })
})
