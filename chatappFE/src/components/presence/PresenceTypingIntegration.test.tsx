// @vitest-environment jsdom

import { describe, expect, it, vi } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"

import TypingIndicator from "./TypingIndicator"
import RoomMembersSidebar from "../chat/RoomMembersSidebar"

const presenceState = {
  typingByRoom: {
    "room-1": {
      user1: true,
    },
  } as Record<string, Record<string, true>>,
  userStatuses: {
    user1: "ONLINE",
  } as Record<string, "ONLINE" | "AWAY" | "OFFLINE">,
}

const userState = {
  users: {
    user1: {
      accountId: "user1",
      username: "alice",
      displayName: "Alice",
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

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({ currentUserId: "me" }),
}))

vi.mock("../../api/room.service", () => ({
  getRoomMembers: vi.fn(async () => [{ userId: "user1", role: "MEMBER" }]),
}))

vi.mock("../user/UserAvatar", () => ({
  default: ({ userId }: { userId: string }) => <div data-testid={`avatar-${userId}`} />,
}))

vi.mock("../user/Username", () => ({
  default: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
}))

describe("Presence typing integration", () => {
  it("shows typing in indicator and room member list for the same user", async () => {
    render(
      <div>
        <TypingIndicator roomId="room-1" />
        <RoomMembersSidebar roomId="room-1" />
      </div>
    )

    expect(screen.getByText("Alice is typing")).toBeTruthy()

    await waitFor(() => {
      expect(document.querySelectorAll(".typing-dot").length).toBeGreaterThanOrEqual(3)
    })
  })
})
