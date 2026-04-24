// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

import UserPopup from "./UserPopup"

const navigateMock = vi.fn()
const sendFriendRequestApiMock = vi.fn()

const overlayState = {
  userId: "user-1",
  rect: {
    left: 10,
    top: 10,
    right: 40,
    bottom: 40,
    width: 30,
    height: 30,
    x: 10,
    y: 10,
    toJSON: () => ({}),
  } as DOMRect,
  source: "CHAT" as const,
  close: vi.fn(),
}

const userState = {
  users: {
    "user-1": {
      accountId: "user-1",
      username: "u1",
      displayName: "User One",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
    me: {
      accountId: "me",
      username: "me",
      displayName: "Me",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
  } as Record<string, any>,
  fetchUsers: vi.fn(),
}

const friendState = {
  map: { "user-1": "NONE", me: "FRIENDS" },
  resolve: vi.fn(),
  setStatus: vi.fn(),
}

const chatState = {
  setActiveRoom: vi.fn(async () => {}),
  sendMessage: vi.fn(async () => {}),
}

const roomState = {
  roomsById: {
    "group-1": {
      id: "group-1",
      type: "GROUP",
      name: "Alpha Group",
      avatarUrl: null,
      createdBy: "me",
      createdAt: "2024-01-01T00:00:00.000Z",
      myRole: "OWNER",
      unreadCount: 0,
    },
  },
}

const startPrivateChatApi = vi.fn(async () => ({ id: "room-1" }))

vi.mock("react-router-dom", () => ({
  useNavigate: () => navigateMock,
}))

vi.mock("../../store/userOverlay.store", () => ({
  useUserOverlay: () => overlayState,
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (s: typeof userState) => unknown) => selector(userState),
}))

vi.mock("../../store/friend.store", () => ({
  useFriendStore: (selector: (s: typeof friendState) => unknown) => selector(friendState),
}))

vi.mock("../../store/chat.store", () => ({
  useChat: () => chatState,
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => roomState,
}))

vi.mock("../../api/friend.service", () => ({
  sendFriendRequestApi: (...args: Parameters<typeof sendFriendRequestApiMock>) =>
    sendFriendRequestApiMock(...args),
  unfriendApi: vi.fn(),
  blockUserApi: vi.fn(),
}))

vi.mock("../../api/room.service", () => ({
  startPrivateChatApi: (...args: Parameters<typeof startPrivateChatApi>) => startPrivateChatApi(...args),
}))

vi.mock("../../api/user.service", () => ({
  getUserByIdApi: vi.fn(async () => ({
    accountId: "user-1",
    username: "u1",
    displayName: "User One",
    avatarUrl: null,
    aboutMe: null,
    backgroundColor: null,
  })),
}))

vi.mock("../chat/EmojiPicker", () => ({
  default: ({
    onEmojiSelect,
    disabled,
  }: {
    onEmojiSelect?: (emoji: string) => void
    disabled?: boolean
  }) => (
    <button
      type="button"
      aria-label="Open emoji picker"
      disabled={disabled}
      onClick={() => onEmojiSelect?.("😀")}
    >
      Emoji
    </button>
  ),
}))

const authState = {
  userId: "me",
}

vi.mock("../../store/auth.store", () => ({
  useAuth: () => authState,
}))

describe("UserPopup self/other actions", () => {
  beforeEach(() => {
    cleanup()
  })

  beforeEach(() => {
    navigateMock.mockReset()
    overlayState.close.mockReset()
    chatState.setActiveRoom.mockClear()
    chatState.sendMessage.mockClear()
    startPrivateChatApi.mockClear()
    sendFriendRequestApiMock.mockReset()
    friendState.resolve.mockReset()
    friendState.setStatus.mockReset()
    friendState.setStatus.mockImplementation((targetUserId: string, status: string) => {
      friendState.map[targetUserId] = status
    })
    friendState.map["user-1"] = "NONE"
    friendState.map.me = "FRIENDS"
    authState.userId = "me"
    overlayState.userId = "user-1"
  })

  it("routes self profile action to settings", () => {
    overlayState.userId = "me"

    render(<UserPopup />)

    fireEvent.click(screen.getByRole("button", { name: "Go to Profile Settings" }))

    expect(navigateMock).toHaveBeenCalledWith("/settings")
    expect(overlayState.close).toHaveBeenCalled()
  })

  it("submits mini chat on Enter and navigates to chat", async () => {
    render(<UserPopup />)

    const input = screen.getByPlaceholderText("Send a message...") as HTMLInputElement
    fireEvent.change(input, { target: { value: "hello" } })
    fireEvent.keyDown(input, { key: "Enter" })

    await waitFor(() => {
      expect(startPrivateChatApi).toHaveBeenCalledWith("user-1")
      expect(chatState.setActiveRoom).toHaveBeenCalledWith("room-1")
      expect(chatState.sendMessage).toHaveBeenCalledWith("room-1", "hello")
      expect(navigateMock).toHaveBeenCalledWith("/chat")
    })
  })

  it("inserts emoji via picker into popup mini-chat input", () => {
    render(<UserPopup />)

    const input = screen.getByPlaceholderText("Send a message...") as HTMLInputElement
    fireEvent.change(input, { target: { value: "hi " } })
    fireEvent.click(screen.getByRole("button", { name: /Open emoji picker/i }))

    expect((screen.getByPlaceholderText("Send a message...") as HTMLInputElement).value).toBe("hi 😀")
  })

  it("switches add-friend action to pending after successful request", async () => {
    sendFriendRequestApiMock.mockResolvedValue(undefined)

    render(<UserPopup />)

    fireEvent.click(screen.getByRole("button", { name: "Add Friend" }))

    await waitFor(() => {
      expect(sendFriendRequestApiMock).toHaveBeenCalledWith("user-1")
      const pendingBtn = screen.getByRole("button", { name: "Pending friend request" })
      expect(pendingBtn).toBeTruthy()
      expect(pendingBtn.hasAttribute("disabled")).toBe(true)
      expect(screen.getByText("Pending")).toBeTruthy()
    })
  })

  it("renders pending immediately when status is already request sent on load", () => {
    friendState.map["user-1"] = "REQUEST_SENT"

    render(<UserPopup />)

    const pendingBtn = screen.getByRole("button", { name: "Pending friend request" })
    expect(pendingBtn).toBeTruthy()
    expect(pendingBtn.hasAttribute("disabled")).toBe(true)
    expect(screen.getByText("Pending")).toBeTruthy()
  })

  it("keeps add-friend action when request send fails", async () => {
    sendFriendRequestApiMock.mockRejectedValue(new Error("network error"))

    render(<UserPopup />)

    fireEvent.click(screen.getByRole("button", { name: "Add Friend" }))

    await waitFor(() => {
      expect(sendFriendRequestApiMock).toHaveBeenCalledWith("user-1")
    })

    expect(screen.getByRole("button", { name: "Add Friend" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Pending friend request" })).toBeNull()
    expect(friendState.setStatus).not.toHaveBeenCalled()
  })

  it("sends selected group invite card from profile popup", async () => {
    render(<UserPopup />)

    fireEvent.click(screen.getByRole("button", { name: "More actions" }))

    const inviteToGroupItem = screen.getByText("Invite to group")
    fireEvent.mouseEnter(inviteToGroupItem)
    fireEvent.click(await screen.findByRole("button", { name: "Alpha Group" }))

    await waitFor(() => {
      expect(startPrivateChatApi).toHaveBeenCalledWith("user-1")
      expect(chatState.setActiveRoom).toHaveBeenCalledWith("room-1")
      expect(chatState.sendMessage).toHaveBeenCalledWith(
        "room-1",
        "",
        [],
        null,
        [
          {
            type: "ROOM_INVITE",
            roomInvite: {
              roomId: "group-1",
              roomName: "Alpha Group",
              roomAvatarUrl: undefined,
            },
          },
        ]
      )
      expect(navigateMock).toHaveBeenCalledWith("/chat")
    })
  })
})
