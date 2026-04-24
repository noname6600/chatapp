/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import MessageInput from "./MessageInput"

const mocks = vi.hoisted(() => ({
  getRoomMembers: vi.fn(),
  sendMessage: vi.fn(),
  clearReply: vi.fn(),
}))

let replyingToState: {
  messageId: string
  senderId: string
  content: string
  deleted: boolean
} | null = null

const userState = {
  users: {
    "user-1": {
      accountId: "user-1",
      username: "alice",
      displayName: "Alice Nguyen",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
  },
  fetchUsers: vi.fn(),
}

vi.mock("../../api/room.service", () => ({
  getRoomMembers: mocks.getRoomMembers,
}))

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    sendMessage: mocks.sendMessage,
    currentUserId: "me",
  }),
}))

vi.mock("../../hooks/useReply", () => ({
  useReply: () => ({
    replyingTo: replyingToState,
    clearReply: mocks.clearReply,
  }),
}))

vi.mock("../../hooks/useEdit", () => ({
  useEdit: () => ({
    editingMessage: null,
    clearEdit: vi.fn(),
  }),
}))

vi.mock("../../websocket/presence.socket", () => ({
  sendTyping: vi.fn(),
  sendStopTyping: vi.fn(),
}))

vi.mock("../../api/upload.service", () => ({
  uploadChatAttachment: vi.fn(),
}))

vi.mock("../../api/chat.service", () => ({
  editMessageApi: vi.fn(),
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: typeof userState) => unknown) => selector(userState),
}))

vi.mock("./EmojiPicker", () => ({
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

describe("MessageInput mention flow", () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    replyingToState = null
    userState.fetchUsers.mockReset()
    mocks.getRoomMembers.mockResolvedValue([
      { userId: "user-1", name: "Alice Nguyen", role: "MEMBER", avatarUrl: null },
    ])
  })

  it("sends successfully after selecting mention without undefined content", async () => {
    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Message/i)) as HTMLTextAreaElement

    fireEvent.change(textarea, { target: { value: "@ali" } })
    textarea.setSelectionRange(4, 4)
    fireEvent.keyUp(textarea, { key: "i" })

    const suggestion = await screen.findByRole("option", { name: /Alice Nguyen/i })
    fireEvent.click(suggestion)

    const sendButton = screen.getByRole("button", { name: /Send/i })
    fireEvent.click(sendButton)

    await waitFor(() => {
      expect(mocks.sendMessage).toHaveBeenCalledTimes(1)
    })

    const args = mocks.sendMessage.mock.calls[0]

    expect(args[0]).toBe("room-1")
    expect(args[1]).toContain("@alice")
    expect(args[1]).not.toContain("undefined")
    expect(args[5]).toEqual(["user-1"])
  })

  it("supports keyboard mention selection without inserting undefined", async () => {
    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Message/i)) as HTMLTextAreaElement

    fireEvent.change(textarea, { target: { value: "@ali" } })
    textarea.setSelectionRange(4, 4)
    fireEvent.keyUp(textarea, { key: "i" })

    await screen.findByRole("option", { name: /Alice Nguyen/i })

    fireEvent.keyDown(textarea, { key: "Enter" })

    fireEvent.click(screen.getByRole("button", { name: /Send/i }))

    await waitFor(() => {
      expect(mocks.sendMessage).toHaveBeenCalledTimes(1)
    })

    const args = mocks.sendMessage.mock.calls[0]

    expect(args[1]).toContain("@alice")
    expect(args[1]).not.toContain("undefined")
    expect(args[5]).toEqual(["user-1"])
  })

  it("sends directly without draft review affordance", async () => {
    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Message/i)) as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: "direct message" } })
    expect(screen.queryByRole("button", { name: /Review/i })).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: /Send/i }))

    await waitFor(() => {
      expect(mocks.sendMessage).toHaveBeenCalledTimes(1)
    })

    const args = mocks.sendMessage.mock.calls[0]
    expect(args[1]).toBe("direct message")
  })

  it("inserts emoji via picker and preserves reply context on send", async () => {
    replyingToState = {
      messageId: "reply-123",
      senderId: "user-1",
      content: "original replied message",
      deleted: false,
    }

    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Message/i)) as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: "replying with emoji " } })
    fireEvent.click(screen.getByRole("button", { name: /Open emoji picker/i }))
    fireEvent.click(screen.getByRole("button", { name: /Send/i }))

    await waitFor(() => {
      expect(mocks.sendMessage).toHaveBeenCalledTimes(1)
    })

    const args = mocks.sendMessage.mock.calls[0]
    expect(args[1]).toContain("😀")
    expect(args[3]).toBe("reply-123")
  })

  it("preserves per-room draft when switching rooms and restores it on return", async () => {
    const { rerender } = render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Message/i)) as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: "draft for room 1" } })

    // Switch to room-2 — draft should be cleared
    rerender(<MessageInput roomId="room-2" />)
    await waitFor(() => {
      expect(textarea.value).toBe("")
    })

    // Switch back to room-1 — draft should be restored
    rerender(<MessageInput roomId="room-1" />)
    await waitFor(() => {
      expect(textarea.value).toBe("draft for room 1")
    })
  })

  it("focuses textarea automatically when replyingTo is set", async () => {
    replyingToState = null
    const { rerender } = render(<MessageInput roomId="room-1" />)

    const textarea = await screen.findByPlaceholderText(/Message/i)
    expect(document.activeElement).not.toBe(textarea)

    replyingToState = {
      messageId: "reply-auto",
      senderId: "user-1",
      content: "focus me",
      deleted: false,
    }
    rerender(<MessageInput roomId="room-1" />)

    await waitFor(() => {
      expect(document.activeElement).toBe(textarea)
    })
  })
})
