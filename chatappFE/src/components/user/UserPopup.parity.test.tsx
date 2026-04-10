// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest"
import { render, screen } from "@testing-library/react"

import UserPopup from "./UserPopup"

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
      username: "",
      displayName: "",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: "bad-color",
    },
  } as Record<string, any>,
  fetchUsers: vi.fn(),
}

const friendState = {
  map: { "user-1": "NONE" },
  resolve: vi.fn(),
  setStatus: vi.fn(),
}

const chatState = {
  setActiveRoom: vi.fn(),
  sendMessage: vi.fn(),
}

const navigateMock = vi.fn()

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

vi.mock("../../store/auth.store", () => ({
  useAuth: () => ({ userId: "me" }),
}))

vi.mock("../../store/chat.store", () => ({
  useChat: () => chatState,
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => ({ roomsById: {} }),
}))

vi.mock("../../api/friend.service", () => ({
  sendFriendRequestApi: vi.fn(),
  unfriendApi: vi.fn(),
  blockUserApi: vi.fn(),
}))

vi.mock("../../api/room.service", () => ({
  startPrivateChatApi: vi.fn(),
}))

vi.mock("../../api/user.service", () => ({
  getUserByIdApi: vi.fn().mockResolvedValue({
    accountId: "user-1",
    username: "",
    displayName: "",
    avatarUrl: null,
    aboutMe: null,
    backgroundColor: "bad-color",
  }),
}))

describe("UserPopup parity mapping", () => {
  beforeEach(() => {
    overlayState.close.mockReset()
    userState.fetchUsers.mockReset()
    friendState.resolve.mockReset()
    friendState.setStatus.mockReset()
  })

  it("renders deterministic fallback identity values used by shared presentation mapping", () => {
    render(<UserPopup />)

    expect(screen.getByText("Unknown User")).toBeTruthy()
    expect(screen.getByText("@unknown")).toBeTruthy()
    expect(screen.queryByTestId("profile-about-text")).toBeNull()

    const avatar = screen.getByRole("img") as HTMLImageElement
    expect(avatar.getAttribute("src")).toBe("/default-avatar.png")
  })
})
