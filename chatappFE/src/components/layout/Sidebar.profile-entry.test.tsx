// @vitest-environment jsdom

import { describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"

import Sidebar from "./Sidebar"

const presenceState = {
  selfPresence: { effectiveStatus: "ONLINE" as const },
  setSelfPresence: vi.fn(),
  setUserStatus: vi.fn(),
}

const friendState = {
  unreadFriendRequestCount: 0,
}

vi.mock("../../hooks/useAuth", () => ({
  useAuth: () => ({
    logout: vi.fn(),
    userId: "me",
    currentUser: {
      accountId: "me",
      username: "john",
      displayName: "John",
      avatarUrl: null,
    },
  }),
}))

vi.mock("../../store/presence.store", () => ({
  usePresenceStore: (selector: (state: typeof presenceState) => unknown) => selector(presenceState),
}))

vi.mock("../../store/friend.store", () => ({
  useFriendStore: (selector: (state: typeof friendState) => unknown) => selector(friendState),
}))

vi.mock("../../api/presence.service", () => ({
  updateMyPresenceApi: vi.fn(),
}))

vi.mock("../user/UserAvatar", () => ({
  default: ({ userId }: { userId: string }) => <div data-testid={`avatar-${userId}`} />, 
}))

vi.mock("../notifications/NotificationBell", () => ({
  default: () => <div data-testid="notification-bell" />,
}))

describe("Sidebar profile entrypoints", () => {
  it("removes Profile nav item and keeps Settings item", () => {
    Object.defineProperty(window, "innerWidth", { value: 1280, configurable: true })

    render(
      <MemoryRouter initialEntries={["/chat"]}>
        <Sidebar />
      </MemoryRouter>
    )

    expect(screen.queryByRole("link", { name: "Profile" })).toBeNull()
    expect(screen.getByRole("link", { name: "Settings" })).toBeTruthy()
  })

  it("exposes avatar and display-name profile entrypoint controls", () => {
    Object.defineProperty(window, "innerWidth", { value: 1280, configurable: true })

    const view = render(
      <MemoryRouter initialEntries={["/chat"]}>
        <Sidebar />
      </MemoryRouter>
    )

    expect(view.getAllByRole("button", { name: "Open profile" }).length).toBeGreaterThan(0)
    expect(view.getAllByRole("button", { name: "John" }).length).toBeGreaterThan(0)

    // Ensure no regression from removal of explicit profile nav item.
    fireEvent.click(view.getAllByRole("link", { name: "Settings" })[0])
    expect(view.queryByRole("link", { name: "Profile" })).toBeNull()
  })
})
