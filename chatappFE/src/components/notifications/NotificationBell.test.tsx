/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import type { CSSProperties } from "react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import NotificationBell from "./NotificationBell"

const mocks = vi.hoisted(() => ({
  fetchNotifications: vi.fn(async () => {}),
  markRead: vi.fn(async () => {}),
  markAllRead: vi.fn(async () => {}),
  setActiveRoom: vi.fn(async () => {}),
  navigate: vi.fn(),
}))

vi.mock("react-router-dom", () => ({
  useNavigate: () => mocks.navigate,
}))

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    setActiveRoom: mocks.setActiveRoom,
  }),
}))

vi.mock("../../store/notification.store", () => ({
  useNotifications: () => ({
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
    unreadCount: 1,
    fetchNotifications: mocks.fetchNotifications,
    markRead: mocks.markRead,
    markAllRead: mocks.markAllRead,
  }),
}))

vi.mock("./NotificationPanel", () => ({
  default: ({
    notifications,
    unreadCount,
    onNotificationClick,
    panelClassName,
    panelStyle,
  }: {
    notifications: Array<{ id: string; type: string; roomId: string | null }>
    unreadCount: number
    onNotificationClick: (notification: { id: string; type: string; roomId: string | null; isRead: boolean }) => Promise<void>
    panelClassName?: string
    panelStyle?: CSSProperties
  }) => (
    <div data-testid="notification-panel" className={panelClassName} style={panelStyle}>
      <div data-testid="notification-unread">{unreadCount}</div>
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
})
