// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"

import RoomMembersSidebar from "./RoomMembersSidebar"

const presenceState = {
  userStatuses: {
    owner: "ONLINE",
    "away-user": "AWAY",
    "offline-user": "OFFLINE",
  },
  typingByRoom: {},
}

const userState = {
  users: {
    owner: {
      accountId: "owner",
      username: "owner",
      displayName: "Owner User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
    "away-user": {
      accountId: "away-user",
      username: "away-user",
      displayName: "Away User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
    "offline-user": {
      accountId: "offline-user",
      username: "offline-user",
      displayName: "Offline User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
  },
  fetchUsers: vi.fn(async () => {}),
}

vi.mock("../../store/presence.store", () => ({
  usePresenceStore: (selector: (state: typeof presenceState) => unknown) => selector(presenceState),
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: typeof userState) => unknown) => selector(userState),
}))

vi.mock("../../api/room.service", () => ({
  getRoomMembers: vi.fn(async () => [
    { userId: "owner", role: "OWNER" },
    { userId: "away-user", role: "MEMBER" },
    { userId: "offline-user", role: "MEMBER" },
  ]),
}))

vi.mock("../user/UserAvatar", () => ({
  default: ({ userId }: { userId: string }) => <div data-testid={`avatar-${userId}`} />,
}))

vi.mock("../user/Username", () => ({
  default: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
}))

describe("RoomMembersSidebar", () => {
  beforeEach(() => {
    localStorage.setItem("my_user_id", "owner")
    presenceState.userStatuses = {
      owner: "ONLINE",
      "away-user": "AWAY",
      "offline-user": "OFFLINE",
    }
  })

  it("renders room members grouped by rich presence status", async () => {
    render(<RoomMembersSidebar roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByText("OWNER")).toBeTruthy()
      expect(screen.getAllByText("AWAY").length).toBeGreaterThan(0)
      expect(screen.getAllByText("OFFLINE").length).toBeGreaterThan(0)
    })

    expect(screen.getByText("Owner User")).toBeTruthy()
    expect(screen.getByText("Away User")).toBeTruthy()
    expect(screen.getByText("Offline User")).toBeTruthy()
  })
})