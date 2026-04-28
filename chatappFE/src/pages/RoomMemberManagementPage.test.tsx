// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"

import RoomMemberManagementPage from "./RoomMemberManagementPage"

const mocks = {
  getRoomMembers: vi.fn(),
  getRoomMembersPaged: vi.fn(),
  getBannedRoomMembersPaged: vi.fn(),
  banMemberApi: vi.fn(),
  kickMemberApi: vi.fn(),
  transferOwnershipApi: vi.fn(),
  unbanMemberApi: vi.fn(),
  bulkBanMembersApi: vi.fn(),
  startPrivateChatApi: vi.fn(),
}

vi.mock("../api/room.service", () => ({
  getRoomMembers: (...args: unknown[]) => mocks.getRoomMembers(...args),
  getRoomMembersPaged: (...args: unknown[]) => mocks.getRoomMembersPaged(...args),
  getBannedRoomMembersPaged: (...args: unknown[]) => mocks.getBannedRoomMembersPaged(...args),
  banMemberApi: (...args: unknown[]) => mocks.banMemberApi(...args),
  kickMemberApi: (...args: unknown[]) => mocks.kickMemberApi(...args),
  transferOwnershipApi: (...args: unknown[]) => mocks.transferOwnershipApi(...args),
  unbanMemberApi: (...args: unknown[]) => mocks.unbanMemberApi(...args),
  bulkBanMembersApi: (...args: unknown[]) => mocks.bulkBanMembersApi(...args),
  startPrivateChatApi: (...args: unknown[]) => mocks.startPrivateChatApi(...args),
}))

vi.mock("../api/friend.service", () => ({
  blockUserApi: vi.fn(async () => undefined),
}))

const fetchUsersMock = vi.fn(async () => undefined)

vi.mock("../store/user.store", () => ({
  useUserStore: (selector: (state: any) => unknown) =>
    selector({
      users: {
        "u-1": { accountId: "u-1", username: "alice", displayName: "Alice", avatarUrl: null },
        "u-2": { accountId: "u-2", username: "bob", displayName: "Bob", avatarUrl: null },
      },
      fetchUsers: fetchUsersMock,
    }),
}))

const setActiveRoomMock = vi.fn(async () => undefined)

vi.mock("../store/chat.store", () => ({
  useChat: () => ({
    setActiveRoom: setActiveRoomMock,
  }),
}))

vi.mock("../store/room.store", () => ({
  useRooms: () => ({
    roomsById: {
      "room-1": { id: "room-1", name: "Dev Group" },
    },
  }),
}))

vi.mock("../components/user/UserAvatar", () => ({
  default: ({ userId }: { userId: string }) => <div data-testid={`avatar-${userId}`} />,
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/chat/rooms/room-1/members"]}>
      <Routes>
        <Route path="/chat/rooms/:roomId/members" element={<RoomMemberManagementPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe("RoomMemberManagementPage", () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    localStorage.setItem("my_user_id", "owner")

    mocks.getRoomMembers.mockReset()
    mocks.getRoomMembersPaged.mockReset()
    mocks.getBannedRoomMembersPaged.mockReset()
    mocks.banMemberApi.mockReset()
    mocks.startPrivateChatApi.mockReset()
    fetchUsersMock.mockReset()

    mocks.getRoomMembers.mockResolvedValue([
      { userId: "owner", role: "OWNER" },
      { userId: "u-1", role: "MEMBER" },
    ])

    mocks.getRoomMembersPaged.mockResolvedValue({
      members: [
        { userId: "u-1", name: "Alice", avatarUrl: null, role: "MEMBER", joinedAt: "2026-01-01T00:00:00Z" },
        { userId: "u-2", name: "Bob", avatarUrl: null, role: "MEMBER", joinedAt: "2026-01-02T00:00:00Z" },
      ],
      page: 0,
      size: 20,
      shown: 2,
      total: 50,
      totalPages: 3,
    })

    mocks.getBannedRoomMembersPaged.mockResolvedValue({
      userIds: [],
      page: 0,
      size: 20,
      shown: 0,
      total: 0,
      totalPages: 0,
    })
  })

  it("renders paginated members with action controls and footer count", async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByText("Room Member Management")).toBeTruthy()
      expect(screen.getByText("Alice")).toBeTruthy()
    })

    expect(screen.getAllByRole("button", { name: /More actions for/i }).length).toBeGreaterThan(0)
    expect(screen.getByText("2 / total 50")).toBeTruthy()
  })

  it("filters current page by search input using username", async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeTruthy()
      expect(screen.getByText("Bob")).toBeTruthy()
    })

    fireEvent.change(screen.getAllByPlaceholderText("Search display name or username")[0], {
      target: { value: "alice" },
    })

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeTruthy()
      expect(screen.queryByText("Bob")).toBeNull()
    })
  })

  it("requires modal confirmation before ban action", async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeTruthy()
    })

    fireEvent.click(screen.getByRole("button", { name: "More actions for Alice" }))
    fireEvent.click(screen.getByRole("button", { name: "Ban" }))

    expect(screen.getByText("Ban this member?")).toBeTruthy()

    fireEvent.click(screen.getByRole("button", { name: "Cancel" }))

    expect(mocks.banMemberApi).not.toHaveBeenCalled()
  })

  it("starts direct message navigation from action menu for non-self users", async () => {
    mocks.startPrivateChatApi.mockResolvedValue({ id: "private-room-1" })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeTruthy()
    })

    fireEvent.click(screen.getByRole("button", { name: "More actions for Alice" }))
    fireEvent.click(screen.getByRole("button", { name: "Message" }))

    await waitFor(() => {
      expect(mocks.startPrivateChatApi).toHaveBeenCalledWith("u-1")
      expect(setActiveRoomMock).toHaveBeenCalledWith("private-room-1")
    })
  })

  it("shows centered bulk ban capsule and requires modal confirmation", async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeTruthy()
    })

    fireEvent.click(screen.getByLabelText("Select Alice"))

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Ban Selected" })).toBeTruthy()
    })

    fireEvent.click(screen.getByRole("button", { name: "Ban Selected" }))

    expect(screen.getByText("Ban selected members?")).toBeTruthy()

    fireEvent.click(screen.getByRole("button", { name: "Cancel" }))
    expect(mocks.bulkBanMembersApi).not.toHaveBeenCalled()
  })
})
