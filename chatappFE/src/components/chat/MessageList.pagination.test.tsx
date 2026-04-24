/* @vitest-environment jsdom */

import { fireEvent, render, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

import MessageList from "./MessageList"
import type { ChatMessage } from "../../types/message"

const loadOlderMessages = vi.fn<(...args: unknown[]) => Promise<void>>()
const removeMessage = vi.fn()
const fetchUsers = vi.fn()
const markRoomRead = vi.fn()
const clearRoomNotifications = vi.fn(async () => {})

let messageState: Record<string, ChatMessage[]> = {}
let roomState: Record<string, { unreadCount?: number }> = {}

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    messagesByRoom: messageState,
    currentUserId: "user-1",
    loadOlderMessages,
    removeMessage,
     setMessageListContainerRef: vi.fn(),
  }),
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: () => ({
    users: {
      "user-1": { accountId: "user-1", username: "user-1", displayName: "User One" },
      "user-2": { accountId: "user-2", username: "user-2", displayName: "User Two" },
    },
    fetchUsers,
  }),
}))

vi.mock("../../store/presence.store", () => ({
  usePresenceStore: (selector: (state: { typingByRoom: Record<string, Record<string, boolean>> }) => unknown) =>
    selector({ typingByRoom: {} }),
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => ({
    roomsById: roomState,
    markRoomRead,
  }),
}))

vi.mock("../../store/notification.store", () => ({
  useNotifications: () => ({
    clearRoomNotifications,
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

vi.mock("./MessageItem", () => ({
  default: ({ message }: { message: ChatMessage }) => (
    <div data-testid={`message-${message.seq}`}>{message.content}</div>
  ),
}))

vi.mock("../presence/TypingUsers", () => ({
  default: () => null,
}))

vi.mock("./ConfirmDeleteDialog", () => ({
  default: () => null,
}))

vi.mock("../message/UnreadMessageIndicator", () => ({
  UnreadMessageIndicator: () => null,
}))

function makeMessage(seq: number): ChatMessage {
  return {
    messageId: `message-${seq}`,
    roomId: "room-1",
    senderId: seq % 2 === 0 ? "user-2" : "user-1",
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
  return Array.from({ length: end - start + 1 }, (_, index) => makeMessage(start + index))
}

describe("MessageList pagination behavior", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    messageState = {
      "room-1": range(51, 100),
      "room-2": range(201, 250).map((message) => ({
        ...message,
        roomId: "room-2",
        messageId: `room-2-${message.seq}`,
      })),
    }
    roomState = {
      "room-1": { unreadCount: 0 },
      "room-2": { unreadCount: 0 },
    }

    Element.prototype.scrollIntoView = vi.fn()
  })

  it("auto-fetches older messages when the list has no overflow", async () => {
    loadOlderMessages.mockImplementation(async () => {
      messageState = {
        ...messageState,
        "room-1": [...range(1, 50), ...messageState["room-1"]],
      }
    })

    render(<MessageList roomId="room-1" />)

    await waitFor(() => expect(loadOlderMessages).toHaveBeenCalledWith("room-1"))
  })

  it("triggers older-message loading when the user scrolls near the top", async () => {
    loadOlderMessages.mockImplementation(async () => {
      messageState = {
        "room-1": [...range(1, 50), ...messageState["room-1"]],
      }
    })

    const { container, rerender } = render(<MessageList roomId="room-1" />)
    const scrollContainer = container.querySelector(".overflow-y-auto") as HTMLDivElement

    let scrollTopValue = 240

    Object.defineProperty(scrollContainer, "scrollTop", {
      configurable: true,
      get: () => scrollTopValue,
      set: (value: number) => {
        scrollTopValue = value
      },
    })
    Object.defineProperty(scrollContainer, "clientHeight", {
      configurable: true,
      get: () => 400,
    })
    Object.defineProperty(scrollContainer, "scrollHeight", {
      configurable: true,
      get: () => 1000,
    })

    scrollTopValue = 240
    fireEvent.scroll(scrollContainer)
    scrollTopValue = 100
    fireEvent.scroll(scrollContainer)

    await waitFor(() => expect(loadOlderMessages).toHaveBeenCalledWith("room-1"))

    rerender(<MessageList roomId="room-1" />)

    await waitFor(() => expect(container.querySelector('[data-testid="message-1"]')).not.toBeNull())
  })

  it("restores scroll position after older messages are prepended", async () => {
    let scrollHeightValue = 1000
    let scrollTopValue = 240
    const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "clientHeight")
    const originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollHeight")

    Object.defineProperty(HTMLElement.prototype, "clientHeight", {
      configurable: true,
      get: () => 400,
    })
    Object.defineProperty(HTMLElement.prototype, "scrollHeight", {
      configurable: true,
      get: () => scrollHeightValue,
    })

    loadOlderMessages.mockImplementation(async () => {
      messageState = {
        "room-1": [...range(1, 50), ...messageState["room-1"]],
      }
      scrollHeightValue = 1600
    })

    const requestAnimationFrameSpy = vi
      .spyOn(window, "requestAnimationFrame")
      .mockImplementation((callback: FrameRequestCallback) => {
        callback(0)
        return 1
      })

    const { container, rerender } = render(<MessageList roomId="room-1" />)
    const scrollContainer = container.querySelector(".overflow-y-auto") as HTMLDivElement

    Object.defineProperty(scrollContainer, "scrollTop", {
      configurable: true,
      get: () => scrollTopValue,
      set: (value: number) => {
        scrollTopValue = value
      },
    })
    Object.defineProperty(scrollContainer, "clientHeight", {
      configurable: true,
      get: () => 400,
    })
    Object.defineProperty(scrollContainer, "scrollHeight", {
      configurable: true,
      get: () => scrollHeightValue,
    })

    scrollTopValue = 240
    fireEvent.scroll(scrollContainer)
    scrollTopValue = 80
    fireEvent.scroll(scrollContainer)

    await waitFor(() => expect(loadOlderMessages).toHaveBeenCalledTimes(1))

    rerender(<MessageList roomId="room-1" />)

    await waitFor(() => expect(scrollTopValue).toBe(680))

    requestAnimationFrameSpy.mockRestore()

    if (originalClientHeight) {
      Object.defineProperty(HTMLElement.prototype, "clientHeight", originalClientHeight)
    }

    if (originalScrollHeight) {
      Object.defineProperty(HTMLElement.prototype, "scrollHeight", originalScrollHeight)
    }
  })

  it("still fetches older messages after switching rooms and returning", async () => {
    loadOlderMessages.mockResolvedValue(undefined)

    const { container, rerender } = render(<MessageList roomId="room-1" />)
    const scrollContainer = container.querySelector(".overflow-y-auto") as HTMLDivElement

    let scrollTopValue = 240
    Object.defineProperty(scrollContainer, "scrollTop", {
      configurable: true,
      get: () => scrollTopValue,
      set: (value: number) => {
        scrollTopValue = value
      },
    })
    Object.defineProperty(scrollContainer, "clientHeight", {
      configurable: true,
      get: () => 400,
    })
    Object.defineProperty(scrollContainer, "scrollHeight", {
      configurable: true,
      get: () => 1000,
    })

    scrollTopValue = 240
    fireEvent.scroll(scrollContainer)
    scrollTopValue = 80
    fireEvent.scroll(scrollContainer)

    await waitFor(() => expect(loadOlderMessages).toHaveBeenCalledWith("room-1"))

    rerender(<MessageList roomId="room-2" />)
    scrollTopValue = 240
    fireEvent.scroll(scrollContainer)

    rerender(<MessageList roomId="room-1" />)
    scrollTopValue = 240
    fireEvent.scroll(scrollContainer)
    scrollTopValue = 80
    fireEvent.scroll(scrollContainer)

    await waitFor(() => {
      const callsForRoom1 = loadOlderMessages.mock.calls.filter(
        ([roomId]) => roomId === "room-1"
      )

      expect(callsForRoom1.length).toBeGreaterThanOrEqual(2)
    })
  })
})