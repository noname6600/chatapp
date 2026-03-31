/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { ChatMessage } from "../../types/message"

import MessageList from "./MessageList"

const mocks = vi.hoisted(() => ({
  loadOlderMessages: vi.fn(async () => {}),
  loadNewerMessages: vi.fn(async () => {}),
  loadMessagesAround: vi.fn(async () => {}),
  removeMessage: vi.fn(),
  setActiveRoom: vi.fn(async () => {}),
  fetchUsers: vi.fn(async () => {}),
  markRoomRead: vi.fn(async () => {}),
  batchScrollToBottom: vi.fn(),
}))

let messageState: Record<string, ChatMessage[]> = {}
let roomState: Record<string, { unreadCount: number }> = {}
let windowMetaState: Record<string, {
  oldestSeq: number | null
  newestSeq: number | null
  latestSeq: number | null
  hasOlder: boolean
  hasNewer: boolean
}> = {}

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    messagesByRoom: messageState,
    windowMetaByRoom: windowMetaState,
    currentUserId: "me",
    setActiveRoom: mocks.setActiveRoom,
    loadOlderMessages: mocks.loadOlderMessages,
    loadNewerMessages: mocks.loadNewerMessages,
    loadMessagesAround: mocks.loadMessagesAround,
    removeMessage: mocks.removeMessage,
     setMessageListContainerRef: vi.fn(),
  }),
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => ({
    roomsById: roomState,
    markRoomRead: mocks.markRoomRead,
  }),
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: () => ({
    users: {
      me: { accountId: "me", username: "me", displayName: "Me", avatarUrl: null },
      user2: { accountId: "user2", username: "user2", displayName: "User Two", avatarUrl: null },
    },
    fetchUsers: mocks.fetchUsers,
  }),
}))

vi.mock("../../hooks/useDelete", () => ({
  useDelete: () => ({
    deletingMessageId: null,
    deletingContent: null,
    clearDeleting: vi.fn(),
  }),
}))

vi.mock("../../api/chat.service", () => ({
  deleteMessageApi: vi.fn(),
}))

vi.mock("../../utils/scrollUtils", () => ({
  isAtBottom: (element: HTMLElement | null) => {
    if (!element) return false
    return element.scrollTop + element.clientHeight >= element.scrollHeight - 50
  },
  batchScrollToBottom: (...args: unknown[]) => mocks.batchScrollToBottom(...args),
}))

vi.mock("./ConfirmDeleteDialog", () => ({
  default: () => null,
}))

vi.mock("./MessageItem", () => ({
  default: ({ message }: { message: ChatMessage }) => (
    <div data-message-id={message.messageId} data-message-seq={message.seq}>
      {message.content}
    </div>
  ),
}))

function makeMessage(seq: number): ChatMessage {
  return {
    messageId: `msg-${seq}`,
    roomId: "room-1",
    senderId: seq % 2 === 0 ? "user2" : "me",
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

function range(start: number, end: number): ChatMessage[] {
  return Array.from({ length: end - start + 1 }, (_, i) => makeMessage(start + i))
}

describe("MessageList unread navigation behavior", () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    Element.prototype.scrollIntoView = vi.fn()
    localStorage.setItem("feature_flag_enableAutoScrollOnNewMessage", "true")

    messageState = { "room-1": range(90, 140) }
    roomState = { "room-1": { unreadCount: 20 } }
    windowMetaState = {
      "room-1": {
        oldestSeq: 90,
        newestSeq: 140,
        latestSeq: 140,
        hasOlder: true,
        hasNewer: false,
      },
    }
  })

  afterEach(() => {
    localStorage.removeItem("feature_flag_enableAutoScrollOnNewMessage")
  })

  it("renders red unread divider when boundary is inside loaded window", async () => {
    render(<MessageList roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByText("Unread messages")).toBeDefined()
    })
  })

  it("does not render unread divider when boundary is outside loaded window", async () => {
    roomState["room-1"].unreadCount = 90

    render(<MessageList roomId="room-1" />)

    await waitFor(() => {
      expect(screen.queryByText("Unread messages")).toBeNull()
    })
  })

  it("shows incremental top indicator when newer messages exist above viewport", async () => {
    roomState["room-1"].unreadCount = 0
    windowMetaState["room-1"] = {
      oldestSeq: 90,
      newestSeq: 140,
      latestSeq: 141,
      hasOlder: true,
      hasNewer: true,
    }

    render(<MessageList roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByText("1 new message")).toBeDefined()
    })
  })

  it("uses unread count label when unread exists even if seq gap is large", async () => {
    roomState["room-1"].unreadCount = 3
    windowMetaState["room-1"] = {
      oldestSeq: 90,
      newestSeq: 140,
      latestSeq: 100_140,
      hasOlder: true,
      hasNewer: true,
    }

    render(<MessageList roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByText("3 unread messages")).toBeDefined()
    })

    expect(screen.queryByText(/behind latest/i)).toBeNull()
  })

  it("does not show behind-latest label when hasNewer is false", async () => {
    roomState["room-1"].unreadCount = 0
    windowMetaState["room-1"] = {
      oldestSeq: 90,
      newestSeq: 140,
      latestSeq: 10_140,
      hasOlder: true,
      hasNewer: false,
    }

    render(<MessageList roomId="room-1" />)

    await waitFor(() => {
      expect(screen.queryByLabelText("Unread messages")).toBeNull()
    })

    expect(screen.queryByText(/behind latest/i)).toBeNull()
  })

  it("loads newer messages when user scrolls near bottom and newer range exists", async () => {
    windowMetaState["room-1"].hasNewer = true

    const { container } = render(<MessageList roomId="room-1" />)
    const scrollContainer = container.querySelector(".overflow-y-auto") as HTMLDivElement

    let scrollTopValue = 500
    Object.defineProperty(scrollContainer, "scrollTop", {
      configurable: true,
      get: () => scrollTopValue,
      set: (value: number) => {
        scrollTopValue = value
      },
    })
    Object.defineProperty(scrollContainer, "clientHeight", {
      configurable: true,
      get: () => 500,
    })
    Object.defineProperty(scrollContainer, "scrollHeight", {
      configurable: true,
      get: () => 1050,
    })

    fireEvent.scroll(scrollContainer)

    await waitFor(() => {
      expect(mocks.loadNewerMessages).toHaveBeenCalledWith("room-1")
    })
  })

  it("marks room read when user jumps to latest", async () => {
    render(<MessageList roomId="room-1" />)

    const jumpButtons = screen.getAllByRole("button", { name: "Jump to latest messages" })
    fireEvent.click(jumpButtons[0])

    await waitFor(() => {
      expect(mocks.markRoomRead).toHaveBeenCalledWith("room-1")
    })
  })

  it("does not auto-scroll for other-user message when user is scrolled up", async () => {
    roomState["room-1"].unreadCount = 0

    const { container, rerender } = render(<MessageList roomId="room-1" />)
    const scrollContainer = container.querySelector(".overflow-y-auto") as HTMLDivElement

    let scrollTopValue = 100
    Object.defineProperty(scrollContainer, "scrollTop", {
      configurable: true,
      get: () => scrollTopValue,
      set: (value: number) => {
        scrollTopValue = value
      },
    })
    Object.defineProperty(scrollContainer, "clientHeight", {
      configurable: true,
      get: () => 500,
    })
    Object.defineProperty(scrollContainer, "scrollHeight", {
      configurable: true,
      get: () => 1000,
    })

    fireEvent.scroll(scrollContainer)

    messageState = {
      "room-1": [...messageState["room-1"], makeMessage(142)],
    }

    rerender(<MessageList roomId="room-1" />)
    await flushAnimationFrames()

    await new Promise((resolve) => setTimeout(resolve, 180))

    expect(mocks.batchScrollToBottom).not.toHaveBeenCalled()
  })

})
