// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

import InviteMembersModal from "./InviteMembersModal"

const getRoomCodeMock = vi.fn()
const getRoomMembersMock = vi.fn()
const clipboardWriteTextMock = vi.fn(async () => undefined)
const fetchUsersMock = vi.fn(async () => undefined)

const mockUserState = {
  users: {} as Record<string, {
    accountId: string
    username: string
    displayName: string
    avatarUrl: string | null
    aboutMe: string | null
    backgroundColor: string | null
  }>,
  fetchUsers: fetchUsersMock,
}

vi.mock("../../api/room.service", () => ({
  getRoomCode: (...args: unknown[]) => getRoomCodeMock(...args),
  getRoomMembers: (...args: unknown[]) => getRoomMembersMock(...args),
}))

vi.mock("../../api/chat.api", () => ({
  chatApi: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: typeof mockUserState) => unknown) => selector(mockUserState),
}))

describe("InviteMembersModal", () => {
  beforeEach(() => {
    getRoomCodeMock.mockReset()
    getRoomMembersMock.mockReset()
    clipboardWriteTextMock.mockReset()
    fetchUsersMock.mockReset()

    mockUserState.users = {
      "member-1": {
        accountId: "member-1",
        username: "member",
        displayName: "Existing Member",
        avatarUrl: null,
        aboutMe: null,
        backgroundColor: null,
      },
      "candidate-1": {
        accountId: "candidate-1",
        username: "bob",
        displayName: "Bob Parker",
        avatarUrl: null,
        aboutMe: "Frontend engineer",
        backgroundColor: null,
      },
    }

    Object.assign(navigator, {
      clipboard: {
        writeText: clipboardWriteTextMock,
      },
    })
  })

  it("shows room code, filters candidates, and sends invite card", async () => {
    getRoomCodeMock.mockResolvedValue("ROOM-CODE-1")
    getRoomMembersMock.mockResolvedValue([
      { userId: "member-1", name: "Existing Member", joinedAt: "2024-01-01", role: "MEMBER" },
    ])

    const onInviteUser = vi.fn(async () => undefined)

    render(
      <InviteMembersModal
        isOpen
        room={{
          id: "room-1",
          type: "GROUP",
          name: "Design Guild",
          createdBy: "me",
          createdAt: "2024-01-01T00:00:00.000Z",
          myRole: "OWNER",
          unreadCount: 0,
        }}
        onClose={() => {}}
        onInviteUser={onInviteUser}
      />
    )

    await waitFor(() => {
      expect(screen.getByDisplayValue("ROOM-CODE-1")).toBeTruthy()
    })

    fireEvent.change(screen.getByPlaceholderText("Search by name or @username..."), {
      target: { value: "BOB" },
    })

    expect(screen.getByText("Bob Parker")).toBeTruthy()
    expect(screen.getByText("@bob")).toBeTruthy()
    expect(screen.getByText("Frontend engineer")).toBeTruthy()

    fireEvent.click(screen.getByRole("button", { name: "Invite" }))

    await waitFor(() => {
      expect(onInviteUser).toHaveBeenCalledWith("candidate-1")
    })

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Sent" })).toBeTruthy()
    })

    fireEvent.click(screen.getByRole("button", { name: "Copy room code" }))

    await waitFor(() => {
      expect(clipboardWriteTextMock).toHaveBeenCalledWith("ROOM-CODE-1")
    })
  })
})
