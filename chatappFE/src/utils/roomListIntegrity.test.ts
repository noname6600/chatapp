import { describe, expect, it } from "vitest"
import type { Room } from "../types/room"
import type { UserProfile } from "../types/user"
import {
  getSplitRoomSections,
  compareRoomsByRecency,
  normalizeRoomForList,
  resolveLastMessageSenderName,
  sortRoomIdsByRecency,
} from "./roomListIntegrity"

const makeRoom = (overrides: Partial<Room>): Room => ({
  id: overrides.id ?? "room-1",
  type: overrides.type ?? "PRIVATE",
  name: overrides.name ?? "Alice",
  avatarUrl: overrides.avatarUrl ?? null,
  createdBy: overrides.createdBy ?? "system",
  createdAt: overrides.createdAt ?? "2026-03-01T00:00:00.000Z",
  myRole: overrides.myRole ?? "MEMBER",
  unreadCount: overrides.unreadCount ?? 0,
  otherUserId: overrides.otherUserId ?? "user-alice",
  lastMessage: overrides.lastMessage ?? null,
})

const makeUser = (overrides: Partial<UserProfile>): UserProfile => ({
  accountId: overrides.accountId ?? "user-id",
  username: overrides.username ?? "user",
  displayName: overrides.displayName ?? "User",
  avatarUrl: overrides.avatarUrl ?? null,
  aboutMe: overrides.aboutMe ?? null,
  backgroundColor: overrides.backgroundColor ?? null,
})

describe("roomListIntegrity", () => {
  it("resolves sender name from usersById map when available", () => {
    const room = makeRoom({
      lastMessage: {
        id: "m-1",
        senderId: "user-b",
        content: "Hello",
        createdAt: "2026-03-20T10:00:00.000Z",
      },
    })

    const usersById = {
      "user-b": makeUser({ accountId: "user-b", displayName: "Bob" }),
    }

    const normalized = normalizeRoomForList(room, "user-a", usersById)

    expect(normalized.lastMessage?.senderName).toBe("Bob")
  })

  it("falls back to room name for private rooms when usersById is not provided", () => {
    const room = makeRoom({
      id: "room-private",
      name: "Alice",
      lastMessage: {
        id: "m-2",
        senderId: "user-b",
        content: "Hi",
        createdAt: "2026-03-20T10:00:00.000Z",
      },
    })

    const normalized = normalizeRoomForList(room, "user-a")

    expect(normalized.lastMessage?.senderName).toBe("Alice")
  })

  it("resolves sender as You when sender is current user", () => {
    const room = makeRoom({
      lastMessage: {
        id: "m-3",
        senderId: "user-me",
        content: "My message",
        createdAt: "2026-03-20T10:00:00.000Z",
      },
    })

    const sender = resolveLastMessageSenderName({
      room,
      senderId: "user-me",
      currentUserId: "user-me",
    })

    expect(sender).toBe("You")
  })

  it("sorts private rooms by latest activity descending", () => {
    const olderPrivate = makeRoom({
      id: "private-old",
      type: "PRIVATE",
      name: "Alice",
      lastMessage: {
        id: "m-4",
        senderId: "user-a",
        content: "Old",
        createdAt: "2026-03-20T09:00:00.000Z",
      },
    })

    const newerPrivate = makeRoom({
      id: "private-new",
      type: "PRIVATE",
      name: "Bob",
      lastMessage: {
        id: "m-5",
        senderId: "user-b",
        content: "New",
        createdAt: "2026-03-20T11:00:00.000Z",
      },
    })

    const sorted = [olderPrivate, newerPrivate].sort(compareRoomsByRecency)

    expect(sorted.map((room) => room.id)).toEqual(["private-new", "private-old"])
  })

  it("uses deterministic tie-breaker when timestamps are equal", () => {
    const roomsById: Record<string, Room> = {
      "room-b": makeRoom({
        id: "room-b",
        lastMessage: {
          id: "m-6",
          senderId: "user-b",
          content: "Hi",
          createdAt: "2026-03-20T10:00:00.000Z",
        },
      }),
      "room-a": makeRoom({
        id: "room-a",
        lastMessage: {
          id: "m-7",
          senderId: "user-a",
          content: "Hi",
          createdAt: "2026-03-20T10:00:00.000Z",
        },
      }),
    }

    expect(sortRoomIdsByRecency(roomsById)).toEqual(["room-a", "room-b"])
  })

  it("preserves group rail order while sorting only private rooms by recency", () => {
    const roomsById: Record<string, Room> = {
      "group-b": makeRoom({
        id: "group-b",
        type: "GROUP",
        name: "Group B",
      }),
      "private-old": makeRoom({
        id: "private-old",
        type: "PRIVATE",
        name: "Alice",
        lastMessage: {
          id: "m-8",
          senderId: "user-a",
          content: "Earlier",
          createdAt: "2026-03-20T09:00:00.000Z",
        },
      }),
      "group-a": makeRoom({
        id: "group-a",
        type: "GROUP",
        name: "Group A",
      }),
      "private-new": makeRoom({
        id: "private-new",
        type: "PRIVATE",
        name: "Bob",
        lastMessage: {
          id: "m-9",
          senderId: "user-b",
          content: "Later",
          createdAt: "2026-03-20T11:00:00.000Z",
        },
      }),
    }

    const sections = getSplitRoomSections(roomsById, [
      "group-b",
      "private-old",
      "group-a",
      "private-new",
    ])

    expect(sections.groupRoomIds).toEqual(["group-b", "group-a"])
    expect(sections.privateRoomIds).toEqual(["private-new", "private-old"])
  })

  it("reorders private panel after newer realtime activity", () => {
    const roomsById: Record<string, Room> = {
      "private-a": makeRoom({
        id: "private-a",
        type: "PRIVATE",
        lastMessage: {
          id: "m-10",
          senderId: "user-a",
          content: "Earlier",
          createdAt: "2026-03-20T09:00:00.000Z",
        },
      }),
      "private-b": makeRoom({
        id: "private-b",
        type: "PRIVATE",
        lastMessage: {
          id: "m-11",
          senderId: "user-b",
          content: "Latest",
          createdAt: "2026-03-20T10:00:00.000Z",
        },
      }),
    }

    expect(sortRoomIdsByRecency(roomsById, ["private-a", "private-b"]))
      .toEqual(["private-b", "private-a"])

    roomsById["private-a"] = {
      ...roomsById["private-a"],
      lastMessage: {
        id: "m-12",
        senderId: "user-a",
        content: "Newest now",
        createdAt: "2026-03-20T12:00:00.000Z",
      },
    }

    expect(sortRoomIdsByRecency(roomsById, ["private-a", "private-b"]))
      .toEqual(["private-a", "private-b"])
  })
})
