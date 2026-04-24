/* @vitest-environment jsdom */

import { act, cleanup, render, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useEffect } from "react"

import { RoomProvider, useRooms } from "./room.store"
import type { Room } from "../types/room"
import { ChatEventType } from "../constants/chatEvents"

const mocks = vi.hoisted(() => ({
  getMyRooms: vi.fn(),
  markRoomReadApi: vi.fn(),
  subscribeRoom: vi.fn(),
  clearRoomNotifications: vi.fn(),
}))

let chatEventHandler: ((event: { type: string; payload: any }) => void) | null = null
let socketOpenHandler: (() => void) | null = null

vi.mock("../api/room.service", () => ({
  getMyRooms: mocks.getMyRooms,
  markRoomReadApi: mocks.markRoomReadApi,
}))

vi.mock("./auth.store", () => ({
  useAuth: () => ({ userId: "me", accessToken: "test-token" }),
}))

vi.mock("./user.store", () => ({
  useUserStore: (selector: (state: any) => unknown) => selector({ users: {} }),
}))

vi.mock("./notification.store", () => ({
  useNotifications: () => ({
    clearRoomNotifications: mocks.clearRoomNotifications,
  }),
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
    localStorage.removeItem("notification_modes_by_room")

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

    mocks.clearRoomNotifications.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
    cleanup()
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

  it("mention-only mode skips unread increments for non-mention messages", async () => {
    localStorage.setItem("notification_modes_by_room", JSON.stringify({ "room-b": "ONLY_MENTION" }))
    const harness = await renderRoomProvider()

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-only-mention-non-target",
          roomId: "room-b",
          senderId: "u2",
          type: "TEXT",
          content: "hello team",
          mentionedUserIds: ["someone-else"],
          attachments: [],
          createdAt: "2026-03-26T11:05:00.000Z",
        },
      })
    })

    expect(harness.store.roomsById["room-b"]?.unreadCount).toBe(0)
  })

  it("mention-only mode increments unread for targeted mentions", async () => {
    localStorage.setItem("notification_modes_by_room", JSON.stringify({ "room-b": "ONLY_MENTION" }))
    const harness = await renderRoomProvider()

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-only-mention-target",
          roomId: "room-b",
          senderId: "u2",
          type: "TEXT",
          content: "@me check this",
          mentionedUserIds: ["me"],
          attachments: [],
          createdAt: "2026-03-26T11:06:00.000Z",
        },
      })
    })

    expect(harness.store.roomsById["room-b"]?.unreadCount).toBe(1)
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

  it("updates background room preview and unread in the same realtime event", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomsById["room-b"]?.unreadCount).toBe(0)
    })

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-invite",
          roomId: "room-b",
          senderId: "u2",
          type: "MIXED",
          content: "",
          blocks: [
            {
              type: "ROOM_INVITE",
              roomInvite: {
                roomId: "group-1",
                roomName: "Engineering",
              },
            },
          ],
          attachments: [],
          createdAt: "2026-03-26T11:12:00.000Z",
        },
      })
    })

    await waitFor(() => {
      const room = harness.store.roomsById["room-b"]
      expect(room?.unreadCount).toBe(1)
      expect(room?.latestMessageAt).toBe("2026-03-26T11:12:00.000Z")
      expect(room?.lastMessage?.content).toBe("[Group Invite: Engineering]")
      expect(room?.lastMessage?.id).toBe("msg-invite")
    })
  })

  it("keeps websocket preview when reconnect snapshot is older", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomsById["room-a"]?.latestMessageAt).toBe("2026-03-26T10:00:00.000Z")
    })

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-live-new",
          roomId: "room-a",
          senderId: "u2",
          type: "TEXT",
          content: "live message",
          attachments: [],
          createdAt: "2026-03-26T11:20:00.000Z",
        },
      })
    })

    await waitFor(() => {
      expect(harness.store.roomsById["room-a"]?.latestMessageAt).toBe("2026-03-26T11:20:00.000Z")
      expect(harness.store.roomsById["room-a"]?.lastMessage?.content).toBe("live message")
    })

    mocks.getMyRooms.mockResolvedValueOnce([
      makeRoom({
        id: "room-a",
        name: "Room A",
        unreadCount: 0,
        latestMessageAt: "2026-03-26T10:00:00.000Z",
        lastMessage: {
          id: "msg-old",
          senderId: "u1",
          content: "old snapshot",
          createdAt: "2026-03-26T10:00:00.000Z",
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

    expect(harness.store.roomsById["room-a"]?.latestMessageAt).toBe("2026-03-26T11:20:00.000Z")
    expect(harness.store.roomsById["room-a"]?.lastMessage?.id).toBe("msg-live-new")
    expect(harness.store.roomsById["room-a"]?.lastMessage?.content).toBe("live message")
    expect(harness.store.roomsById["room-a"]?.unreadCount).toBe(0)
  })

  it("keeps deterministic room ordering when timestamps are equal", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomOrder).toEqual(["room-a", "room-b"])
    })

    vi.useFakeTimers()

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-same-ts",
          roomId: "room-b",
          senderId: "u2",
          type: "TEXT",
          content: "same ts",
          attachments: [],
          createdAt: "2026-03-26T10:00:00.000Z",
        },
      })
    })

    await act(async () => {
      vi.advanceTimersByTime(301)
    })

    expect(harness.store.roomOrder).toEqual(["room-a", "room-b"])
    vi.useRealTimers()
  })

  it("reconciles unknown room on first incoming message so it appears without refresh", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomsById["room-c"]).toBeUndefined()
    })

    mocks.getMyRooms.mockResolvedValueOnce([
      makeRoom({
        id: "room-a",
        name: "Room A",
        unreadCount: 0,
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
        unreadCount: 0,
        latestMessageAt: "2026-03-26T09:00:00.000Z",
        lastMessage: {
          id: "msg-b",
          senderId: "u2",
          content: "b",
          createdAt: "2026-03-26T09:00:00.000Z",
        },
      }),
      makeRoom({
        id: "room-c",
        type: "PRIVATE",
        name: "New Chat",
        unreadCount: 1,
        latestMessageAt: "2026-03-26T11:35:00.000Z",
        lastMessage: {
          id: "msg-c-1",
          senderId: "u3",
          content: "hello first time",
          createdAt: "2026-03-26T11:35:00.000Z",
        },
      }),
    ])

    await act(async () => {
      chatEventHandler?.({
        type: ChatEventType.MESSAGE_SENT,
        payload: {
          messageId: "msg-c-1",
          roomId: "room-c",
          senderId: "u3",
          type: "TEXT",
          content: "hello first time",
          attachments: [],
          createdAt: "2026-03-26T11:35:00.000Z",
        },
      })
    })

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 180))
    })

    await waitFor(() => {
      expect(harness.store.roomsById["room-c"]).toBeDefined()
      expect(harness.store.roomsById["room-c"]?.unreadCount).toBe(1)
      expect(harness.store.roomsById["room-c"]?.lastMessage?.content).toBe("hello first time")
    })

    expect(mocks.subscribeRoom).toHaveBeenCalledWith("room-c")
  })

  it("removes a room locally without waiting for a reload", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomOrder).toEqual(["room-a", "room-b"])
    })

    await act(async () => {
      harness.store.removeRoom("room-a")
    })

    expect(harness.store.roomsById["room-a"]).toBeUndefined()
    expect(harness.store.roomOrder).toEqual(["room-b"])
  })

  it("reconciliation drops rooms no longer returned by the backend", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomOrder).toEqual(["room-a", "room-b"])
    })

    mocks.getMyRooms.mockResolvedValueOnce([
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

    await act(async () => {
      await harness.store.reconcileRoomState()
    })

    await waitFor(() => {
      expect(harness.store.roomsById["room-a"]).toBeUndefined()
      expect(harness.store.roomOrder).toEqual(["room-b"])
    })
  })

  it("markRoomRead triggers room-scoped notification clear when authenticated", async () => {
    const harness = await renderRoomProvider()

    await waitFor(() => {
      expect(harness.store.roomOrder).toEqual(["room-a", "room-b"])
    })

    mocks.markRoomReadApi.mockResolvedValueOnce(undefined)

    await act(async () => {
      await harness.store.markRoomRead("room-a")
    })

    expect(mocks.markRoomReadApi).toHaveBeenCalledWith("room-a")
    expect(mocks.clearRoomNotifications).toHaveBeenCalledWith("room-a")
  })
})
