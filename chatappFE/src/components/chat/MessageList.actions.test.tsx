/* @vitest-environment jsdom */

import { render, fireEvent, waitFor, screen, cleanup } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import MessageList from "./MessageList"
import type { ChatMessage } from "../../types/message"

// Use vi.hoisted so these are accessible inside vi.mock factory callbacks
const {
  clearDeleting,
  setDeleting,
  removeMessage,
  loadOlderMessages,
  fetchUsers,
  deleteMessageApiMock,
} = vi.hoisted(() => ({
  clearDeleting: vi.fn(),
  setDeleting: vi.fn(),
  removeMessage: vi.fn(),
  loadOlderMessages: vi.fn(),
  fetchUsers: vi.fn(),
  deleteMessageApiMock: vi.fn(),
}))

// Shared mutable state for delete store (mutated by setDeleting/clearDeleting impls)
let deletingMessageId: string | null = null
let deletingContent: string | null = null

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    messagesByRoom: {
      "room-1": [makeMessage(1), makeMessage(2)],
    },
    currentUserId: "user-1",
    loadOlderMessages,
    removeMessage,
    setMessageListContainerRef: vi.fn(),
  }),
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: () => ({
    users: {
      "user-1": { accountId: "user-1", username: "user1", displayName: "User One" },
    },
    fetchUsers,
  }),
}))

vi.mock("../../hooks/useDelete", () => ({
  useDelete: () => ({
    get deletingMessageId() { return deletingMessageId },
    get deletingContent() { return deletingContent },
    setDeleting,
    clearDeleting,
  }),
}))

vi.mock("../../api/chat.service", () => ({
  deleteMessageApi: deleteMessageApiMock,
}))

vi.mock("./MessageItem", () => ({
  default: ({
    message,
    onShowDeleteDialog,
  }: {
    message: ChatMessage
    onShowDeleteDialog?: () => void
  }) => (
    <div data-testid={`message-${message.seq}`}>
      {message.content}
      <button
        data-testid={`delete-${message.seq}`}
        onClick={() => {
          setDeleting(message.messageId, message.content ?? "")
          onShowDeleteDialog?.()
        }}
      >
        Delete
      </button>
    </div>
  ),
}))

vi.mock("./ConfirmDeleteDialog", () => ({
  default: ({
    messageId,
    onConfirm,
    onCancel,
  }: {
    messageId: string
    onConfirm: (id: string) => void
    onCancel: () => void
  }) => (
    <div data-testid="confirm-delete-dialog">
      <button data-testid="confirm-btn" onClick={() => onConfirm(messageId)}>
        Confirm
      </button>
      <button data-testid="cancel-btn" onClick={onCancel}>
        Cancel
      </button>
    </div>
  ),
}))

vi.mock("../../store/presence.store", () => ({
  usePresenceStore: (selector: (s: { typingByRoom: Record<string, Record<string, boolean>> }) => unknown) =>
    selector({ typingByRoom: {} }),
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => ({
    roomsById: {},
    markRoomRead: vi.fn(),
  }),
}))

vi.mock("../presence/TypingUsers", () => ({ default: () => null }))
vi.mock("../message/UnreadMessageIndicator", () => ({
  UnreadMessageIndicator: () => null,
}))

function makeMessage(seq: number): ChatMessage {
  return {
    messageId: `msg-${seq}`,
    roomId: "room-1",
    senderId: "user-1",
    seq,
    type: "TEXT",
    content: `Message ${seq}`,
    replyToMessageId: null,
    clientMessageId: null,
    createdAt: new Date(Date.UTC(2026, 0, 1, 0, 0, seq)).toISOString(),
    editedAt: null,
    deleted: false,
    attachments: [],
    reactions: [],
  }
}

describe("MessageList delete action behavior", () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    deletingMessageId = null
    deletingContent = null
    Element.prototype.scrollIntoView = vi.fn()

    // Give the mock fns actual implementations that update shared state
    setDeleting.mockImplementation((id: string, content: string) => {
      deletingMessageId = id
      deletingContent = content
    })
    clearDeleting.mockImplementation(() => {
      deletingMessageId = null
      deletingContent = null
    })
  })

  it("shows ConfirmDeleteDialog when a delete action is triggered", async () => {
    const { rerender } = render(<MessageList roomId="room-1" />)

    // Trigger delete on message 1
    fireEvent.click(screen.getByTestId("delete-1"))

    // Simulate Zustand update by rerendering (since mock uses getters)
    rerender(<MessageList roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByTestId("confirm-delete-dialog")).toBeDefined()
    })
  })

  it("calls deleteMessageApi and removeMessage on confirm", async () => {
    deleteMessageApiMock.mockResolvedValue(undefined)

    const { rerender } = render(<MessageList roomId="room-1" />)

    fireEvent.click(screen.getByTestId("delete-1"))
    rerender(<MessageList roomId="room-1" />)

    await waitFor(() => screen.getByTestId("confirm-delete-dialog"))

    fireEvent.click(screen.getByTestId("confirm-btn"))

    await waitFor(() => {
      expect(deleteMessageApiMock).toHaveBeenCalledWith("msg-1")
      expect(removeMessage).toHaveBeenCalledWith("msg-1", "room-1")
      expect(clearDeleting).toHaveBeenCalled()
    })
  })

  it("calls clearDeleting on cancel without removing message", async () => {
    const { rerender } = render(<MessageList roomId="room-1" />)

    fireEvent.click(screen.getByTestId("delete-1"))
    rerender(<MessageList roomId="room-1" />)

    await waitFor(() => screen.getByTestId("confirm-delete-dialog"))

    fireEvent.click(screen.getByTestId("cancel-btn"))

    await waitFor(() => {
      expect(clearDeleting).toHaveBeenCalled()
      expect(deleteMessageApiMock).not.toHaveBeenCalled()
      expect(removeMessage).not.toHaveBeenCalled()
    })
  })
})
