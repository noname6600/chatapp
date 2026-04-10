// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"

import MessageBlocks from "./MessageBlocks"

const joinRoomByInviteApiMock = vi.fn(async () => undefined)
const clipboardWriteTextMock = vi.fn(async () => undefined)
const loadRoomsMock = vi.fn(async () => undefined)
const setActiveRoomMock = vi.fn(async () => undefined)
const navigateMock = vi.fn()
let roomsByIdMock: Record<string, unknown> = {}

Object.defineProperty(navigator, "clipboard", {
  value: {
    writeText: (...args: unknown[]) => clipboardWriteTextMock(...args),
  },
  configurable: true,
})

vi.mock("../../api/room.service", () => ({
  joinRoomByInviteApi: (...args: unknown[]) => joinRoomByInviteApiMock(...args),
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => ({
    loadRooms: loadRoomsMock,
    roomsById: roomsByIdMock,
  }),
}))

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    setActiveRoom: setActiveRoomMock,
  }),
}))

vi.mock("react-router-dom", () => ({
  useNavigate: () => navigateMock,
}))

vi.mock("../user/Username", () => ({
  default: ({ userId, children }: { userId: string; children: React.ReactNode }) => (
    <span data-testid={`mention-user-${userId}`}>{children}</span>
  ),
}))

describe("MessageBlocks", () => {
  afterEach(() => {
    joinRoomByInviteApiMock.mockReset()
    clipboardWriteTextMock.mockReset()
    loadRoomsMock.mockReset()
    setActiveRoomMock.mockReset()
    navigateMock.mockReset()
    roomsByIdMock = {}
    cleanup()
  })

  it("renders ordered text, link, and asset blocks", () => {
    render(
      <MessageBlocks
        blocks={[
          { type: "TEXT", text: "Before https://example.com/docs" },
          {
            type: "ASSET",
            attachment: {
              type: "IMAGE",
              url: "https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg",
              fileName: "a.jpg",
            },
          },
          { type: "TEXT", text: "After" },
        ]}
      />
    )

    expect(screen.getByRole("link", { name: "https://example.com/docs" })).toBeTruthy()
    expect(screen.getByAltText("a.jpg")).toBeTruthy()
    expect(screen.getByText("After")).toBeTruthy()
  })

  it("renders mentions as @DisplayName and wires clickable mention wrapper", () => {
    render(
      <MessageBlocks
        blocks={[{ type: "TEXT", text: "Hi @alice" }]}
        resolveMentionLabel={(token) => (token === "alice" ? "Alice Nguyen" : token)}
        resolveMentionUserId={(token) => (token === "alice" ? "user-1" : null)}
      />
    )

    expect(screen.getByText("@Alice Nguyen")).toBeTruthy()
    expect(screen.getByTestId("mention-user-user-1")).toBeTruthy()
  })

  it("skips empty text blocks and keeps render spacing clean", () => {
    const { container } = render(
      <MessageBlocks
        blocks={[
          { type: "TEXT", text: "   " },
          {
            type: "ASSET",
            attachment: {
              type: "IMAGE",
              url: "https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg",
              fileName: "a.jpg",
            },
          },
          { type: "TEXT", text: "Visible text" },
          { type: "TEXT", text: "" },
        ]}
      />
    )

    expect(container.querySelectorAll(".text-sm.text-gray-800")).toHaveLength(1)
    expect(screen.getAllByAltText("a.jpg")).toHaveLength(1)
    expect(screen.getByText("Visible text")).toBeTruthy()
  })

  it("returns no rendered output when all text blocks are empty", () => {
    const { container } = render(
      <MessageBlocks
        blocks={[
          { type: "TEXT", text: "" },
          { type: "TEXT", text: "   " },
        ]}
      />
    )

    expect(container.firstChild).toBeNull()
  })

  it("renders ROOM_INVITE block with fallback room name and members text", () => {
    render(
      <MessageBlocks
        blocks={[
          {
            type: "ROOM_INVITE",
            roomInvite: {
              roomId: "room-1",
            },
          },
        ]}
      />
    )

    expect(screen.getByText("Group Invite")).toBeTruthy()
    expect(screen.getByText("Unnamed room")).toBeTruthy()
    expect(screen.getByText("Use link or code to join")).toBeTruthy()
    expect(screen.getByText(/^Code: room-1$/)).toBeTruthy()
    expect(screen.getByRole("button", { name: "Copy invite link" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Join Group" })).toBeTruthy()
  })

  it("copies invite link from ROOM_INVITE card", async () => {
    render(
      <MessageBlocks
        blocks={[
          {
            type: "ROOM_INVITE",
            roomInvite: {
              roomId: "room-copy",
              roomName: "Copy Room",
            },
          },
        ]}
      />
    )

    fireEvent.click(screen.getByRole("button", { name: "Copy invite link" }))

    await waitFor(() => {
      expect(clipboardWriteTextMock).toHaveBeenCalledWith(
        expect.stringContaining("/chat?join=room-copy")
      )
    })
  })

  it("shows Joined state when target room already exists in rooms store", () => {
    roomsByIdMock = {
      "room-joined": {
        id: "room-joined",
      },
    }

    render(
      <MessageBlocks
        blocks={[
          {
            type: "ROOM_INVITE",
            roomInvite: {
              roomId: "room-joined",
              roomName: "Joined Room",
            },
          },
        ]}
      />
    )

    const joinedButton = screen.getByRole("button", { name: "Joined" })
    expect(joinedButton).toBeTruthy()
    expect(joinedButton.getAttribute("disabled")).not.toBeNull()
  })

  it("joins room and routes to chat on successful invite join", async () => {
    joinRoomByInviteApiMock.mockResolvedValueOnce(undefined)

    render(
      <MessageBlocks
        blocks={[
          {
            type: "ROOM_INVITE",
            roomInvite: {
              roomId: "room-join",
              roomName: "Join Me",
            },
          },
        ]}
      />
    )

    fireEvent.click(screen.getByRole("button", { name: "Join Group" }))

    await waitFor(() => {
      expect(joinRoomByInviteApiMock).toHaveBeenCalledWith("room-join")
      expect(loadRoomsMock).toHaveBeenCalled()
      expect(setActiveRoomMock).toHaveBeenCalledWith("room-join")
      expect(navigateMock).toHaveBeenCalledWith("/chat")
    })
  })

  it("shows unavailable state when invite join is denied", async () => {
    joinRoomByInviteApiMock.mockRejectedValueOnce(new Error("Room not found"))

    render(
      <MessageBlocks
        blocks={[
          {
            type: "ROOM_INVITE",
            roomInvite: {
              roomId: "room-missing",
              roomName: "Missing",
            },
          },
        ]}
      />
    )

    fireEvent.click(screen.getByRole("button", { name: "Join Group" }))

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Unavailable" }).getAttribute("disabled")).not.toBeNull()
      expect(screen.getByText("Room not found")).toBeTruthy()
    })
  })
})