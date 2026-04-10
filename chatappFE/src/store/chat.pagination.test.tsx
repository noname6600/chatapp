/* @vitest-environment jsdom */

import { act, render, waitFor } from "@testing-library/react"
import { describe, expect, it, beforeEach, vi } from "vitest"
import { useEffect } from "react"

import { ChatEventType } from "../constants/chatEvents"
import { ChatProvider, useChat } from "./chat.store"
import type { ChatMessage, MessagePage } from "../types/message"

const mocks = vi.hoisted(() => ({
  getLatestMessages: vi.fn<(...args: unknown[]) => Promise<MessagePage>>(),
  getMessagesBefore: vi.fn<(...args: unknown[]) => Promise<MessagePage>>(),
  getMessageRange: vi.fn<(...args: unknown[]) => Promise<ChatMessage[]>>(),
  getMessagesAround: vi.fn<(...args: unknown[]) => Promise<ChatMessage[]>>(),
  sendMessageApi: vi.fn(),
  subscribeRoom: vi.fn(),
  onSocketOpen: vi.fn((callback: () => void) => {
    void callback
    return () => {}
  }),
  getMyProfileApi: vi.fn(async () => ({ accountId: "me" })),
}))

let chatEventHandler: ((event: { type: string; payload: ChatMessage }) => void) | null = null

vi.mock("../api/chat.service", () => ({
  getLatestMessages: mocks.getLatestMessages,
  getMessagesBefore: mocks.getMessagesBefore,
  getMessageRange: mocks.getMessageRange,
  getMessagesAround: mocks.getMessagesAround,
  sendMessageApi: mocks.sendMessageApi,
}))

vi.mock("../websocket/chat.socket", () => ({
  subscribeRoom: mocks.subscribeRoom,
  onSocketOpen: mocks.onSocketOpen,
  onChatEvent: (callback: (event: { type: string; payload: ChatMessage }) => void) => {
    chatEventHandler = callback
    return () => {
      chatEventHandler = null
    }
  },
}))

vi.mock("../api/user.service", () => ({
  getMyProfileApi: mocks.getMyProfileApi,
}))

function makeMessage(seq: number, overrides: Partial<ChatMessage> = {}): ChatMessage {
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
    ...overrides,
  }
}

function range(start: number, end: number): ChatMessage[] {
  return Array.from({ length: end - start + 1 }, (_, index) => makeMessage(start + index))
}

function rangeForRoom(roomId: string, start: number, end: number): ChatMessage[] {
  return range(start, end).map((message) => ({
    ...message,
    roomId,
    messageId: `${roomId}-${message.seq}`,
  }))
}

function Probe({ onReady }: { onReady: (value: ReturnType<typeof useChat>) => void }) {
  const chat = useChat()

  useEffect(() => {
    onReady(chat)
  }, [chat, onReady])

  return null
}

async function renderChatProvider() {
  let chatApi: ReturnType<typeof useChat> | null = null

  render(
    <ChatProvider>
      <Probe onReady={(value) => {
        chatApi = value
      }} />
    </ChatProvider>
  )

  await waitFor(() => expect(chatApi).not.toBeNull())

  return {
    get chat() {
      if (!chatApi) {
        throw new Error("Chat API not initialized")
      }

      return chatApi
    },
  }
}

describe("chat pagination behavior", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chatEventHandler = null
    mocks.getMessageRange.mockResolvedValue([])
    mocks.getMessagesAround.mockResolvedValue([])
  })

  it("sorts messages ascending after setActiveRoom", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: [makeMessage(53), makeMessage(51), makeMessage(52)],
      hasMore: true,
    })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
    })

    expect(harness.chat.messagesByRoom["room-1"].map((message) => message.seq)).toEqual([51, 52, 53])
  })

  it("prepends older messages in ascending order and filters duplicates", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: [makeMessage(53), makeMessage(54), makeMessage(55)],
      hasMore: true,
    })
    mocks.getMessagesBefore.mockResolvedValueOnce({
      messages: [makeMessage(52), makeMessage(51), makeMessage(53)],
      hasMore: false,
    })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
      await harness.chat.loadOlderMessages("room-1")
    })

    expect(harness.chat.messagesByRoom["room-1"].map((message) => message.seq)).toEqual([51, 52, 53, 54, 55])
    expect(harness.chat.messagesByRoom["room-1"].filter((message) => message.seq === 53)).toHaveLength(1)
  })

  it("stops fetching older messages after backend reports no more history", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: range(51, 100),
      hasMore: true,
    })
    mocks.getMessagesBefore.mockResolvedValueOnce({
      messages: [],
      hasMore: false,
    })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
      await harness.chat.loadOlderMessages("room-1")
      await harness.chat.loadOlderMessages("room-1")
    })

    expect(mocks.getMessagesBefore).toHaveBeenCalledTimes(1)
    expect(harness.chat.messagesByRoom["room-1"]).toHaveLength(50)
  })

  it("keeps state consistent when a websocket message arrives during older-message fetch", async () => {
    let resolveOlderPage: ((value: MessagePage) => void) | null = null

    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: range(51, 100),
      hasMore: true,
    })
    mocks.getMessagesBefore.mockImplementationOnce(
      () =>
        new Promise<MessagePage>((resolve) => {
          resolveOlderPage = resolve
        })
    )

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
    })

    const pendingOlderLoad = harness.chat.loadOlderMessages("room-1")

    await waitFor(() => expect(mocks.getMessagesBefore).toHaveBeenCalledTimes(1))

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: makeMessage(101),
      })
    })

    await act(async () => {
      resolveOlderPage?.({
        messages: range(1, 50),
        hasMore: false,
      })
    })

    await act(async () => {
      await pendingOlderLoad
    })

    expect(harness.chat.messagesByRoom["room-1"].map((message) => message.seq)).toEqual(range(1, 101).map((message) => message.seq))
    expect(harness.chat.messagesByRoom["room-1"].at(-1)?.seq).toBe(101)
  })

  it("keeps pagination state isolated across rooms", async () => {
    mocks.getLatestMessages
      .mockResolvedValueOnce({
        messages: rangeForRoom("room-1", 51, 100),
        hasMore: false,
      })
      .mockResolvedValueOnce({
        messages: rangeForRoom("room-2", 151, 200),
        hasMore: true,
      })

    mocks.getMessagesBefore.mockResolvedValueOnce({
      messages: rangeForRoom("room-2", 101, 150),
      hasMore: false,
    })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
      await harness.chat.setActiveRoom("room-2")
      await harness.chat.loadOlderMessages("room-1")
      await harness.chat.loadOlderMessages("room-2")
    })

    expect(mocks.getMessagesBefore).toHaveBeenCalledTimes(1)
    expect(mocks.getMessagesBefore).toHaveBeenCalledWith("room-2", 151)
    expect(harness.chat.messagesByRoom["room-2"].map((message) => message.seq)).toEqual(range(101, 200).map((message) => message.seq))
  })

  it("reconciles optimistic and server messages by clientMessageId to prevent duplicates", async () => {
    mocks.sendMessageApi.mockImplementationOnce(async (payload: {
      roomId: string
      content: string
      replyToMessageId?: string
      clientMessageId?: string
    }) =>
      makeMessage(301, {
        messageId: "server-301",
        roomId: payload.roomId,
        senderId: "me",
        seq: 301,
        content: payload.content,
        replyToMessageId: payload.replyToMessageId ?? null,
        clientMessageId: payload.clientMessageId ?? null,
      })
    )

    const harness = await renderChatProvider()

    await waitFor(() => expect(harness.chat.currentUserId).toBe("me"))

    await act(async () => {
      await harness.chat.sendMessage("room-1", "Reply once", [], "message-1")
    })

    const roomMessages = harness.chat.messagesByRoom["room-1"]

    expect(roomMessages).toHaveLength(1)
    expect(roomMessages[0].messageId).toBe("server-301")
    expect(roomMessages[0].replyToMessageId).toBe("message-1")
  })

  it("transitions unresolved optimistic message from pending to failed after timeout", async () => {
    const harness = await renderChatProvider()
    await waitFor(() => expect(harness.chat.currentUserId).toBe("me"))
    vi.useFakeTimers()

    try {
      mocks.sendMessageApi.mockImplementationOnce(
        () => new Promise<ChatMessage>(() => {})
      )

      await act(async () => {
        void harness.chat.sendMessage("room-1", "Eventually fails")
      })

      expect(harness.chat.messagesByRoom["room-1"]).toHaveLength(1)
      expect(harness.chat.messagesByRoom["room-1"][0].deliveryStatus).toBe("pending")

      await act(async () => {
        vi.advanceTimersByTime(15001)
      })

      expect(harness.chat.messagesByRoom["room-1"][0].deliveryStatus).toBe("failed")
    } finally {
      vi.useRealTimers()
    }
  })

  it("retries failed optimistic message with same clientMessageId and reconciles to server message", async () => {
    mocks.sendMessageApi
      .mockRejectedValueOnce(new Error("network"))
      .mockImplementationOnce(async (payload: {
        roomId: string
        content: string
        clientMessageId?: string
      }) =>
        makeMessage(302, {
          messageId: "server-302",
          roomId: payload.roomId,
          senderId: "me",
          content: payload.content,
          clientMessageId: payload.clientMessageId ?? null,
        })
      )

    const harness = await renderChatProvider()
    await waitFor(() => expect(harness.chat.currentUserId).toBe("me"))

    await act(async () => {
      await harness.chat.sendMessage("room-1", "Retry me")
    })

    const failed = harness.chat.messagesByRoom["room-1"][0]
    expect(failed.deliveryStatus).toBe("failed")
    const originalClientMessageId = failed.clientMessageId

    await act(async () => {
      await harness.chat.retryMessage("room-1", failed.messageId)
    })

    expect(mocks.sendMessageApi).toHaveBeenCalledTimes(2)
    expect(mocks.sendMessageApi.mock.calls[1][0].clientMessageId).toBe(originalClientMessageId)

    const roomMessages = harness.chat.messagesByRoom["room-1"]
    expect(roomMessages).toHaveLength(1)
    expect(roomMessages[0].messageId).toBe("server-302")
    expect(roomMessages[0].deliveryStatus).toBe("sent")
  })

  it("marks unmatched stale pending optimistic messages as failed on latest refresh", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: [],
      hasMore: false,
    })

    const harness = await renderChatProvider()
    await waitFor(() => expect(harness.chat.currentUserId).toBe("me"))

    await act(async () => {
      harness.chat.upsertMessage({
        ...makeMessage(999, {
          seq: Number.MAX_SAFE_INTEGER,
          messageId: "temp-old",
          roomId: "room-1",
          senderId: "me",
          clientMessageId: "client-old",
          createdAt: new Date(Date.now() - 16_000).toISOString(),
        }),
        deliveryStatus: "pending",
      })
    })

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
    })

    expect(harness.chat.messagesByRoom["room-1"]).toHaveLength(1)
    expect(harness.chat.messagesByRoom["room-1"][0].messageId).toBe("temp-old")
    expect(harness.chat.messagesByRoom["room-1"][0].deliveryStatus).toBe("failed")
  })

  it("uses latest-window fallback when unread count is zero", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: range(451, 500),
      hasMore: true,
    })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1", 0)
    })

    expect(mocks.getMessageRange).not.toHaveBeenCalled()
    expect(harness.chat.messagesByRoom["room-1"][0].seq).toBe(451)
    expect(harness.chat.messagesByRoom["room-1"].at(-1)?.seq).toBe(500)
  })

  it("merges first-unread anchor window with latest window when boundary is outside latest", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: range(451, 500),
      hasMore: true,
    })
    mocks.getMessageRange.mockResolvedValueOnce(range(391, 441))

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1", 100)
    })

    // firstUnread = 500 - 100 + 1 = 401, so requested range is [376, 426]
    expect(mocks.getMessageRange).toHaveBeenCalledWith("room-1", 376, 426)
    expect(harness.chat.messagesByRoom["room-1"][0].seq).toBe(391)
    expect(harness.chat.messagesByRoom["room-1"].at(-1)?.seq).toBe(500)
  })

  it("preserves bidirectional pagination continuity after loading around a jump target", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: range(451, 500),
      hasMore: true,
    })
    mocks.getMessageRange
      .mockResolvedValueOnce(range(252, 301))
    mocks.getMessagesAround.mockResolvedValueOnce(range(201, 251))
    mocks.getMessagesBefore.mockResolvedValueOnce({
      messages: range(151, 200),
      hasMore: true,
    })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1", 0)
      await harness.chat.loadMessagesAround("room-1", "message-226")
    })

    expect(harness.chat.windowMetaByRoom["room-1"]?.oldestSeq).toBe(201)
    expect(harness.chat.windowMetaByRoom["room-1"]?.newestSeq).toBe(251)
    expect(harness.chat.windowMetaByRoom["room-1"]?.latestSeq).toBe(500)
    expect(harness.chat.windowMetaByRoom["room-1"]?.hasOlder).toBe(true)
    expect(harness.chat.windowMetaByRoom["room-1"]?.hasNewer).toBe(true)

    await act(async () => {
      await harness.chat.loadOlderMessages("room-1")
      await harness.chat.loadNewerMessages("room-1")
    })

    expect(harness.chat.messagesByRoom["room-1"][0].seq).toBe(151)
    expect(harness.chat.messagesByRoom["room-1"].at(-1)?.seq).toBe(301)
  })

  it("optimistic attachment send does not advance latestSeqByRoom", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: range(1, 5),
      hasMore: false,
    })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
    })

    // latestSeq is 5 after loading the initial window
    expect(harness.chat.windowMetaByRoom["room-1"]?.latestSeq).toBe(5)

    await act(async () => {
      harness.chat.upsertMessage({
        ...makeMessage(999, {
          seq: Number.MAX_SAFE_INTEGER,
          messageId: "temp-attach-1",
          roomId: "room-1",
          senderId: "me",
          clientMessageId: "cid-attach-1",
          type: "ATTACHMENT",
        }),
        deliveryStatus: "pending",
      })
    })

    // Optimistic placeholder must NOT advance latestSeq beyond the last confirmed server seq
    expect(harness.chat.windowMetaByRoom["room-1"]?.latestSeq).toBe(5)
    // hasNewer must remain false — the optimistic tail doesn't mean we are behind
    expect(harness.chat.windowMetaByRoom["room-1"]?.hasNewer).toBe(false)
  })

  it("latestSeqByRoom is correctly updated after server confirmation of attachment send", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: range(1, 5),
      hasMore: false,
    })

    const clientId = "cid-attach-2"

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
    })

    // Insert optimistic placeholder
    await act(async () => {
      harness.chat.upsertMessage({
        ...makeMessage(999, {
          seq: Number.MAX_SAFE_INTEGER,
          messageId: "temp-attach-2",
          roomId: "room-1",
          senderId: "me",
          clientMessageId: clientId,
          type: "ATTACHMENT",
        }),
        deliveryStatus: "pending",
      })
    })

    // Server confirms with real seq 6 — reconciliation should replace the temp
    await act(async () => {
      harness.chat.upsertMessage(
        makeMessage(6, {
          messageId: "server-6",
          roomId: "room-1",
          senderId: "me",
          clientMessageId: clientId,
          type: "ATTACHMENT",
        })
      )
    })

    // After reconciliation: temp removed, real seq 6 present
    expect(harness.chat.messagesByRoom["room-1"].find((m) => m.messageId === "temp-attach-2")).toBeUndefined()
    expect(harness.chat.messagesByRoom["room-1"].find((m) => m.messageId === "server-6")).toBeDefined()
    // latestSeq should now reflect the confirmed server seq
    expect(harness.chat.windowMetaByRoom["room-1"]?.latestSeq).toBe(6)
    // hasNewer must still be false — we are at the latest after self-send
    expect(harness.chat.windowMetaByRoom["room-1"]?.hasNewer).toBe(false)
  })

  it("converges to full 1-10 history after send/join/send and refresh", async () => {
    mocks.getLatestMessages
      .mockResolvedValueOnce({
        // user joins mid-flow and initially sees only first 4 persisted messages
        messages: range(1, 4),
        hasMore: false,
      })
      .mockResolvedValueOnce({
        // refresh should converge to authoritative persisted history
        messages: range(1, 10),
        hasMore: false,
      })
      .mockResolvedValue({
        // fallback for any reconciliation-triggered additional refresh call
        messages: range(1, 10),
        hasMore: false,
      })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
    })

    await act(async () => {
      range(5, 10).forEach((message) => {
        chatEventHandler?.({
          type: ChatEventType.MESSAGE_SENT,
          payload: message,
        })
      })
    })

    expect(harness.chat.messagesByRoom["room-1"].map((message) => message.seq)).toEqual(
      range(1, 10).map((message) => message.seq)
    )

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
    })

    expect(harness.chat.messagesByRoom["room-1"].map((message) => message.seq)).toEqual(
      range(1, 10).map((message) => message.seq)
    )
  })

  it("shows complete latest persisted window when joining mid-conversation", async () => {
    mocks.getLatestMessages.mockResolvedValueOnce({
      messages: range(146, 195),
      hasMore: true,
    })

    const harness = await renderChatProvider()

    await act(async () => {
      await harness.chat.setActiveRoom("room-1")
    })

    const seqs = harness.chat.messagesByRoom["room-1"].map((message) => message.seq)
    expect(seqs[0]).toBe(146)
    expect(seqs.at(-1)).toBe(195)
    expect(seqs).toHaveLength(50)
  })

  it("keeps two independent sessions aligned after refresh", async () => {
    mocks.getLatestMessages
      .mockResolvedValueOnce({ messages: range(1, 10), hasMore: false })
      .mockResolvedValueOnce({ messages: range(1, 10), hasMore: false })

    const sessionA = await renderChatProvider()
    const sessionB = await renderChatProvider()

    await act(async () => {
      await sessionA.chat.setActiveRoom("room-1")
      await sessionB.chat.setActiveRoom("room-1")
    })

    expect(sessionA.chat.messagesByRoom["room-1"].map((message) => message.seq)).toEqual(
      sessionB.chat.messagesByRoom["room-1"].map((message) => message.seq)
    )
  })
})