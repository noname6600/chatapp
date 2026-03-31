// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest"
import { render, screen } from "@testing-library/react"

import TypingIndicator from "./TypingIndicator"

const presenceState = {
  typingByRoom: {} as Record<string, Record<string, true>>,
}

const userState = {
  users: {
    user1: { displayName: "Alice", username: "alice", avatarUrl: null },
    user2: { displayName: "Bob", username: "bob", avatarUrl: null },
    user3: { displayName: "Cara", username: "cara", avatarUrl: null },
    user4: { displayName: "Dan", username: "dan", avatarUrl: null },
  },
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

vi.mock("../user/UserAvatar", () => ({
  default: ({ userId }: { userId: string }) => <div data-testid={`avatar-${userId}`} />,
}))

describe("TypingIndicator", () => {
  beforeEach(() => {
    presenceState.typingByRoom = {}
  })

  it("shows one remote name and excludes self typing", () => {
    presenceState.typingByRoom = { "room-1": { me: true, user1: true } }

    render(<TypingIndicator roomId="room-1" />)

    expect(screen.getByText("Alice is typing")).toBeTruthy()
    expect(screen.queryByText("Me is typing")).toBeNull()
  })

  it("collapses to a generic summary when more than three remote users are typing", () => {
    presenceState.typingByRoom = {
      "room-1": { user1: true, user2: true, user3: true, user4: true },
    }

    render(<TypingIndicator roomId="room-1" />)

    expect(screen.getByText("Multiple people are typing")).toBeTruthy()
  })
})