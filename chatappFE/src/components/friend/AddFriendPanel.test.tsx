/* @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { AddFriendPanel } from "./AddFriendPanel"

const friendServiceMocks = vi.hoisted(() => ({
  sendFriendRequestApi: vi.fn(),
}))

const userServiceMocks = vi.hoisted(() => ({
  searchUserByUsernameApi: vi.fn(),
}))

let currentFriendStatus:
  | "SELF"
  | "FRIENDS"
  | "REQUEST_SENT"
  | "REQUEST_RECEIVED"
  | "BLOCKED_BY_ME"
  | "BLOCKED_ME"
  | "NONE"
  | undefined = "NONE"

const updateUserLocal = vi.fn()
const openUserOverlay = vi.fn()
const setFriendStatus = vi.fn((userId: string, status: typeof currentFriendStatus) => {
  void userId
  currentFriendStatus = status
})

vi.mock("../../api/friend.service", () => ({
  sendFriendRequestApi: friendServiceMocks.sendFriendRequestApi,
}))

vi.mock("../../api/user.service", () => ({
  searchUserByUsernameApi: userServiceMocks.searchUserByUsernameApi,
}))

vi.mock("../../hooks/useFriendStatus", () => ({
  useFriendStatus: () => currentFriendStatus,
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: { updateUserLocal: typeof updateUserLocal }) => unknown) =>
    selector({ updateUserLocal }),
}))

vi.mock("../../store/userOverlay.store", () => ({
  useUserOverlay: (selector: (state: { open: typeof openUserOverlay }) => unknown) =>
    selector({ open: openUserOverlay }),
}))

vi.mock("../../store/friend.store", () => ({
  useFriendStore: (selector: (state: { setStatus: typeof setFriendStatus }) => unknown) =>
    selector({ setStatus: setFriendStatus }),
}))

describe("AddFriendPanel", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    currentFriendStatus = "NONE"
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  const searchFor = async (value: string) => {
    render(<AddFriendPanel />)

    fireEvent.change(screen.getByPlaceholderText("Search by username"), {
      target: { value },
    })

    await act(async () => {
      vi.advanceTimersByTime(400)
      await Promise.resolve()
      await Promise.resolve()
    })
  }

  it("shows result cards for a valid username search", async () => {
    userServiceMocks.searchUserByUsernameApi.mockResolvedValue([
      {
        accountId: "user-1",
        username: "alice",
        displayName: "Alice",
        avatarUrl: null,
        aboutMe: null,
        backgroundColor: null,
      },
      {
        accountId: "user-2",
        username: "alina",
        displayName: "Alina",
        avatarUrl: null,
        aboutMe: null,
        backgroundColor: null,
      },
    ])

    await searchFor("alice")

    expect(screen.getByText("Results")).toBeTruthy()
    expect(screen.getAllByTestId("friend-search-card")).toHaveLength(2)
    expect(screen.getByText("Alice")).toBeTruthy()
  })

  it("shows a no-result state when the username does not exist", async () => {
    userServiceMocks.searchUserByUsernameApi.mockResolvedValue([])

    await searchFor("missing-user")

    expect(screen.getByText("No matches found yet.")).toBeTruthy()
    expect(screen.queryByTestId("friend-search-card")).toBeNull()
  })

  it("opens the matching profile when the resolved card is clicked", async () => {
    userServiceMocks.searchUserByUsernameApi.mockResolvedValue([
      {
        accountId: "user-2",
        username: "bob",
        displayName: "Bob",
        avatarUrl: null,
        aboutMe: null,
        backgroundColor: null,
      },
    ])

    await searchFor("bob")

    fireEvent.click(screen.getByRole("button", { name: "Open profile for Bob" }))

    expect(openUserOverlay).toHaveBeenCalledTimes(1)
    expect(openUserOverlay).toHaveBeenCalledWith(
      "user-2",
      expect.anything(),
      "FRIEND_SEARCH"
    )
  })

  it("sends a request from the resolved card and changes to pending", async () => {
    userServiceMocks.searchUserByUsernameApi.mockResolvedValue([
      {
        accountId: "user-3",
        username: "cora",
        displayName: "Cora",
        avatarUrl: null,
        aboutMe: null,
        backgroundColor: null,
      },
    ])
    friendServiceMocks.sendFriendRequestApi.mockResolvedValue(undefined)

    await searchFor("cora")

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Add Friend" }))
      await Promise.resolve()
    })

    expect(friendServiceMocks.sendFriendRequestApi).toHaveBeenCalledWith("user-3")
    expect(setFriendStatus).toHaveBeenCalledWith("user-3", "REQUEST_SENT")
    expect(screen.getByRole("button", { name: "Pending" }).hasAttribute("disabled")).toBe(true)
  })

  it("does not render recommendation content", () => {
    render(<AddFriendPanel />)

    expect(screen.queryByText("People you may know")).toBeNull()
  })
})
