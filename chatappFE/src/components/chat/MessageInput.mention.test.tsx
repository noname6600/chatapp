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

    const textarea = (await screen.findByPlaceholderText(/Type a message/i)) as HTMLTextAreaElement

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

    const textarea = (await screen.findByPlaceholderText(/Type a message/i)) as HTMLTextAreaElement

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

  it("opens draft review and sends edited review text", async () => {
    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Type a message/i)) as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: "original draft" } })

    fireEvent.click(screen.getByRole("button", { name: /Review/i }))

    const reviewText = screen.getByPlaceholderText(/Write your message/i) as HTMLTextAreaElement
    fireEvent.change(reviewText, { target: { value: "edited from review" } })

    fireEvent.click(screen.getByRole("button", { name: /Send reviewed message/i }))

    await waitFor(() => {
      expect(mocks.sendMessage).toHaveBeenCalledTimes(1)
    })

    const args = mocks.sendMessage.mock.calls[0]
    expect(args[1]).toBe("edited from review")
  })

  it("opens and closes draft review modal", async () => {
    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Type a message/i)) as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: "draft to review" } })

    fireEvent.click(screen.getByRole("button", { name: /Review/i }))
    expect(screen.getByText(/Review draft/i)).toBeTruthy()

    fireEvent.click(screen.getByRole("button", { name: /Close review modal/i }))

    await waitFor(() => {
      expect(screen.queryByText(/Review draft/i)).toBeNull()
    })
  })

  it("allows editing text inside draft review modal", async () => {
    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Type a message/i)) as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: "initial draft" } })
    fireEvent.click(screen.getByRole("button", { name: /Review/i }))

    const reviewTextarea = screen.getByPlaceholderText(/Write your message/i) as HTMLTextAreaElement
    fireEvent.change(reviewTextarea, { target: { value: "changed in modal" } })

    expect(reviewTextarea.value).toBe("changed in modal")
  })

  it("cancel closes review modal without sending message", async () => {
    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Type a message/i)) as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: "cancel me" } })
    fireEvent.click(screen.getByRole("button", { name: /Review/i }))

    fireEvent.click(screen.getByRole("button", { name: /^Cancel$/i }))

    await waitFor(() => {
      expect(screen.queryByText(/Review draft/i)).toBeNull()
    })

    expect(mocks.sendMessage).not.toHaveBeenCalled()
  })

  it("preserves reply context when sending from draft review", async () => {
    replyingToState = {
      messageId: "reply-123",
      senderId: "user-1",
      content: "original replied message",
      deleted: false,
    }

    render(<MessageInput roomId="room-1" />)

    const textarea = (await screen.findByPlaceholderText(/Type a message/i)) as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: "replying through review" } })
    fireEvent.click(screen.getByRole("button", { name: /Review/i }))

    expect(screen.getByText(/Reply context kept/i)).toBeTruthy()

    fireEvent.click(screen.getByRole("button", { name: /Send reviewed message/i }))

    await waitFor(() => {
      expect(mocks.sendMessage).toHaveBeenCalledTimes(1)
    })

    const args = mocks.sendMessage.mock.calls[0]
    expect(args[3]).toBe("reply-123")
  })
})
