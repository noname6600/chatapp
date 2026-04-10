// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"

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
  afterEach(() => {
    cleanup()
  })

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

  it("opens chat when friend row surface is clicked", () => {
    presenceState.getUserStatus.mockReturnValue("ONLINE")
    const onChat = vi.fn()

    render(<FriendRow userId="friend" variant="friend" onChat={onChat} />)

    fireEvent.click(screen.getByTestId("friend-row-friend-friend"))

    expect(onChat).toHaveBeenCalledWith("friend")
  })

  it("does not open chat when clicking secondary actions", () => {
    presenceState.getUserStatus.mockReturnValue("ONLINE")
    const onChat = vi.fn()
    const onRemove = vi.fn()

    render(<FriendRow userId="friend" variant="friend" onChat={onChat} onRemove={onRemove} />)

    fireEvent.click(screen.getByLabelText("More actions"))
    fireEvent.click(screen.getByText("Remove Friend"))

    expect(onRemove).toHaveBeenCalledTimes(1)
    expect(onChat).not.toHaveBeenCalled()
  })

  it("keeps pending actions button-driven", () => {
    presenceState.getUserStatus.mockReturnValue("ONLINE")
    const onAccept = vi.fn()
    const onDecline = vi.fn()

    render(
      <FriendRow
        userId="friend"
        variant="pending"
        onAccept={onAccept}
        onDecline={onDecline}
      />
    )

    fireEvent.click(screen.getByLabelText("Accept request"))
    fireEvent.click(screen.getByLabelText("Decline request"))

    expect(onAccept).toHaveBeenCalledWith("friend")
    expect(onDecline).toHaveBeenCalledWith("friend")
  })
})