/* @vitest-environment jsdom */

import { act, render, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useEffect } from "react"

import { RoomProvider, useRooms } from "./room.store"
import type { Room } from "../types/room"
import { ChatEventType } from "../constants/chatEvents"

const mocks = vi.hoisted(() => ({
  getMyRooms: vi.fn(),
  markRoomReadApi: vi.fn(),
  subscribeRoom: vi.fn(),
}))

let chatEventHandler: ((event: { type: string; payload: any }) => void) | null = null
let socketOpenHandler: (() => void) | null = null

vi.mock("../api/room.service", () => ({
  getMyRooms: mocks.getMyRooms,
  markRoomReadApi: mocks.markRoomReadApi,
}))

vi.mock("./auth.store", () => ({
  useAuth: () => ({ userId: "me" }),
}))

vi.mock("./user.store", () => ({
  useUserStore: (selector: (state: any) => unknown) => selector({ users: {} }),
}))

vi.mock("../hooks/usePopulateUserCache", () => ({
  usePopulateUserCache: () => {},
}))

vi.mock("../websocket/chat.socket", () => ({
  onChatEvent: (callback: (event: { type: string; payload: any }) => void) => {
    chatEventHandler = callback
    return () => {
      chatEventHandler = null
    }
  },
  onSocketOpen: (callback: () => void) => {
    socketOpenHandler = callback
    return () => {
      socketOpenHandler = null
    }
  },
  subscribeRoom: mocks.subscribeRoom,
}))

function makeRoom(overrides: Partial<Room> = {}): Room {
  return {
    id: overrides.id ?? "room-1",
    type: overrides.type ?? "GROUP",
    name: overrides.name ?? "Room",
    createdBy: "me",
    createdAt: overrides.createdAt ?? "2026-01-01T00:00:00.000Z",
    myRole: "OWNER",
    unreadCount: overrides.unreadCount ?? 0,
    latestMessageAt: overrides.latestMessageAt ?? overrides.lastMessage?.createdAt ?? null,
    lastMessage: overrides.lastMessage,
    avatarUrl: null,
    otherUserId: null,
  }
}

function Probe({ onReady }: { onReady: (value: ReturnType<typeof useRooms>) => void }) {
  const store = useRooms()

  useEffect(() => {
    onReady(store)
  }, [store, onReady])

  return null
}

async function renderRoomProvider() {
  let storeApi: ReturnType<typeof useRooms> | null = null

  render(
    <RoomProvider>
      <Probe onReady={(value) => {
        storeApi = value
      }} />
    </RoomProvider>
  )

  await waitFor(() => expect(storeApi).not.toBeNull())

  return {
    get store() {
      if (!storeApi) {
        throw new Error("Room store not initialized")
      }
      return storeApi
    },
  }
}

describe("room.store", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chatEventHandler = null
    socketOpenHandler = null
    localStorage.removeItem("notification_mutes_by_room")

    mocks.getMyRooms.mockResolvedValue([
      makeRoom({
        id: "room-a",
        name: "Room A",
        latestMessageAt: "2026-03-26T10:00:00.000Z",
        lastMessage: {
          id: "msg-a",
          senderId: "u1",
          content: "a",
          createdAt: "2026-03-26T10:00:00.000Z",
        },
      }),
      makeRoom({
        id: "room-b",
        name: "Room B",
        latestMessageAt: "2026-03-26T09:00:00.000Z",
        lastMessage: {
          id: "msg-b",
          senderId: "u2",
          content: "b",
          createdAt: "2026-03-26T09:00:00.000Z",
        },
      }),
    ])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it("room list re-sorts when message arrives for background room", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomOrder).toEqual(["room-a", "room-b"])
    })

    expect(mocks.subscribeRoom).toHaveBeenCalledWith("room-a")
    expect(mocks.subscribeRoom).toHaveBeenCalledWith("room-b")

    vi.useFakeTimers()

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-new",
          roomId: "room-b",
          senderId: "u2",
          type: "TEXT",
          content: "new message",
          attachments: [],
          createdAt: "2026-03-26T11:00:00.000Z",
        },
      })
    })

    expect(harness.store.roomOrder).toEqual(["room-a", "room-b"])

    await act(async () => {
      vi.advanceTimersByTime(301)
    })

    expect(harness.store.roomOrder).toEqual(["room-b", "room-a"])
    vi.useRealTimers()
  })

  it("muted room does not increment unread on incoming message", async () => {
    localStorage.setItem("notification_mutes_by_room", JSON.stringify({ "room-b": true }))

    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomsById["room-b"]?.unreadCount).toBe(0)
    })

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-muted",
          roomId: "room-b",
          senderId: "u2",
          type: "TEXT",
          content: "muted room message",
          attachments: [],
          createdAt: "2026-03-26T11:05:00.000Z",
        },
      })
    })

    expect(harness.store.roomsById["room-b"]?.unreadCount).toBe(0)
  })

  it("self-sent message does not increment unread", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomsById["room-a"]?.unreadCount).toBe(0)
    })

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-self",
          roomId: "room-a",
          senderId: "me",
          type: "TEXT",
          content: "my own message",
          attachments: [],
          createdAt: "2026-03-26T11:06:00.000Z",
        },
      })
    })

    expect(harness.store.roomsById["room-a"]?.unreadCount).toBe(0)
  })

  it("other-user message increments unread", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomsById["room-a"]?.unreadCount).toBe(0)
    })

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-other",
          roomId: "room-a",
          senderId: "user2",
          type: "TEXT",
          content: "from other user",
          attachments: [],
          createdAt: "2026-03-26T11:07:00.000Z",
        },
      })
    })

    expect(harness.store.roomsById["room-a"]?.unreadCount).toBe(1)
  })

  it("reconciles unread count from backend snapshot after reconnect", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomsById["room-a"]?.unreadCount).toBe(0)
    })

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-transient",
          roomId: "room-a",
          senderId: "u2",
          type: "TEXT",
          content: "transient",
          attachments: [],
          createdAt: "2026-03-26T11:10:00.000Z",
        },
      })
    })

    expect(harness.store.roomsById["room-a"]?.unreadCount).toBe(1)

    mocks.getMyRooms.mockResolvedValueOnce([
      makeRoom({
        id: "room-a",
        name: "Room A",
        unreadCount: 0,
        latestMessageAt: "2026-03-26T11:10:00.000Z",
        lastMessage: {
          id: "msg-transient",
          senderId: "u2",
          content: "transient",
          createdAt: "2026-03-26T11:10:00.000Z",
        },
      }),
      makeRoom({
        id: "room-b",
        name: "Room B",
        unreadCount: 0,
        latestMessageAt: "2026-03-26T09:00:00.000Z",
        lastMessage: {
          id: "msg-b",
          senderId: "u2",
          content: "b",
          createdAt: "2026-03-26T09:00:00.000Z",
        },
      }),
    ])

    await act(async () => {
      socketOpenHandler?.()
    })

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 140))
    })

    await waitFor(() => {
      expect(harness.store.roomsById["room-a"]?.unreadCount).toBe(0)
    })
  })
})
