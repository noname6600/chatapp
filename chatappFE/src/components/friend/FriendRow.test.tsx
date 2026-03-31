// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest"
import { render, screen } from "@testing-library/react"

import { FriendRow } from "./FriendRow"

const presenceState = {
  getUserStatus: vi.fn<(userId: string) => "ONLINE" | "AWAY" | "OFFLINE">(),
}

const userState = {
  users: {
    friend: {
      accountId: "friend",
      username: "friend",
      displayName: "Friendly User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
  },
}

vi.mock("../../store/presence.store", () => ({
  usePresenceStore: (selector: (state: typeof presenceState) => unknown) => selector(presenceState),
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: typeof userState) => unknown) => selector(userState),
}))

describe("FriendRow", () => {
  beforeEach(() => {
    presenceState.getUserStatus.mockReset()
  })

  it("renders away and offline labels from rich presence status", () => {
    presenceState.getUserStatus.mockReturnValue("AWAY")
    const { rerender } = render(<FriendRow userId="friend" variant="friend" />)

    expect(screen.getByText("Away")).toBeTruthy()

    presenceState.getUserStatus.mockReturnValue("OFFLINE")
    rerender(<FriendRow userId="friend" variant="friend" />)

    expect(screen.getByText("Offline")).toBeTruthy()
  })
})