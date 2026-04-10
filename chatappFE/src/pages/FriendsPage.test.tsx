/* @vitest-environment jsdom */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"

import FriendsPage from "./FriendsPage"

const friendServiceMocks = vi.hoisted(() => ({
  getFriendsApi: vi.fn(),
  getIncomingApi: vi.fn(),
  getOutgoingApi: vi.fn(),
  getUnreadFriendRequestCountApi: vi.fn(),
  acceptFriendApi: vi.fn(),
  declineFriendApi: vi.fn(),
  cancelRequestApi: vi.fn(),
  unfriendApi: vi.fn(),
}))

const userStoreState = {
  fetchUsers: vi.fn(),
}

const presenceState = {
  userStatuses: {
    friend: "ONLINE",
    friend2: "OFFLINE",
    in1: "OFFLINE",
    out1: "OFFLINE",
  } as Record<string, "ONLINE" | "AWAY" | "OFFLINE">,
}

const friendStoreState = {
  unreadFriendRequestCount: 3,
  setUnreadCount: vi.fn(),
  setStatus: vi.fn(),
}

const roomStoreState = {
  loadRooms: vi.fn(async () => {}),
}

const chatStoreState = {
  setActiveRoom: vi.fn(async () => {}),
}

vi.mock("../api/friend.service", () => ({
  getFriendsApi: friendServiceMocks.getFriendsApi,
  getIncomingApi: friendServiceMocks.getIncomingApi,
  getOutgoingApi: friendServiceMocks.getOutgoingApi,
  getUnreadFriendRequestCountApi: friendServiceMocks.getUnreadFriendRequestCountApi,
  acceptFriendApi: friendServiceMocks.acceptFriendApi,
  declineFriendApi: friendServiceMocks.declineFriendApi,
  cancelRequestApi: friendServiceMocks.cancelRequestApi,
  unfriendApi: friendServiceMocks.unfriendApi,
}))

vi.mock("../api/room.service", () => ({
  startPrivateChatApi: vi.fn(),
}))

vi.mock("../store/user.store", () => ({
  useUserStore: (selector: (state: typeof userStoreState) => unknown) => selector(userStoreState),
}))

vi.mock("../store/presence.store", () => ({
  usePresenceStore: (selector: (state: typeof presenceState) => unknown) => selector(presenceState),
}))

vi.mock("../store/friend.store", () => ({
  useFriendStore: (selector: (state: typeof friendStoreState) => unknown) => selector(friendStoreState),
}))

vi.mock("../store/room.store", () => ({
  useRooms: () => roomStoreState,
}))

vi.mock("../store/chat.store", () => ({
  useChat: () => chatStoreState,
}))

vi.mock("../websocket/friendship.socket", () => ({
  FriendshipEventType: {
    FRIEND_REQUEST_RECEIVED: "FRIEND_REQUEST_RECEIVED",
    FRIEND_REQUEST_ACCEPTED: "FRIEND_REQUEST_ACCEPTED",
    FRIEND_REQUEST_DECLINED: "FRIEND_REQUEST_DECLINED",
    FRIEND_REQUEST_CANCELLED: "FRIEND_REQUEST_CANCELLED",
    FRIEND_STATUS_CHANGED: "FRIEND_STATUS_CHANGED",
  },
  onFriendshipEvent: vi.fn(() => () => {}),
}))

vi.mock("../components/friend/AddFriendPanel", () => ({
  AddFriendPanel: () => <div data-testid="add-friend-panel">Add panel</div>,
}))

vi.mock("../components/friend/FriendRow", () => ({
  FriendRow: ({ userId, variant }: { userId: string; variant: "pending" | "friend" }) => (
    <div data-testid={`friend-row-${variant}-${userId}`}>{`${variant}-${userId}`}</div>
  ),
}))

const renderPage = () =>
  render(
    <MemoryRouter>
      <FriendsPage />
    </MemoryRouter>
  )

describe("FriendsPage shell and tabs", () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    userStoreState.fetchUsers.mockResolvedValue(undefined)
    friendServiceMocks.getFriendsApi.mockResolvedValue(["friend", "friend2"])
    friendServiceMocks.getIncomingApi.mockResolvedValue(["in1"])
    friendServiceMocks.getOutgoingApi.mockResolvedValue(["out1"])
    friendServiceMocks.getUnreadFriendRequestCountApi.mockResolvedValue({ unreadCount: 3 })
  })

  it("renders settings-style shell with summary cards", async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByText("Friends")).toBeTruthy()
      expect(screen.getByText("Total Friends")).toBeTruthy()
      expect(screen.getByText("Online Now")).toBeTruthy()
      expect(screen.getByText("Pending Requests")).toBeTruthy()
    })
  })

  it("switches tabs and keeps content inside the shell", async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByTestId("friend-row-friend-friend")).toBeTruthy()
    })

    fireEvent.click(screen.getByRole("button", { name: "All (2)" }))

    await waitFor(() => {
      expect(screen.getByTestId("friend-row-friend-friend2")).toBeTruthy()
    })

    fireEvent.click(screen.getByRole("button", { name: /Pending \(2\)/ }))

    await waitFor(() => {
      expect(screen.getByText("Incoming (1)")).toBeTruthy()
      expect(screen.getByText("Outgoing (1)")).toBeTruthy()
      expect(screen.getByTestId("friend-row-pending-in1")).toBeTruthy()
      expect(screen.getByTestId("friend-row-pending-out1")).toBeTruthy()
    })

    fireEvent.click(screen.getByRole("button", { name: "Add Friend" }))

    await waitFor(() => {
      expect(screen.getByTestId("add-friend-panel")).toBeTruthy()
      expect(screen.getByText("Find and Add Friends")).toBeTruthy()
    })
  })
})
