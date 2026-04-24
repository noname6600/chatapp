import { describe, expect, it } from "vitest"

import {
  normalizeRoomNotificationMode,
  shouldDeliverRoomEventByMode,
  shouldCountNotificationAsUnreadByMode,
} from "./notificationModePolicy"

describe("notificationModePolicy", () => {
  it("normalizes missing mode using legacy mute value", () => {
    expect(normalizeRoomNotificationMode(undefined, true)).toBe("NOTHING")
    expect(normalizeRoomNotificationMode(undefined, false)).toBe("NO_RESTRICT")
  })

  it("enforces room delivery matrix", () => {
    expect(shouldDeliverRoomEventByMode("NO_RESTRICT", false)).toBe(true)
    expect(shouldDeliverRoomEventByMode("ONLY_MENTION", false)).toBe(false)
    expect(shouldDeliverRoomEventByMode("ONLY_MENTION", true)).toBe(true)
    expect(shouldDeliverRoomEventByMode("NOTHING", true)).toBe(false)
  })

  it("applies mode policy to unread counting for room notifications", () => {
    const base = {
      id: "n1",
      type: "MESSAGE" as const,
      referenceId: "r1",
      roomId: "room-1",
      senderName: "Alice",
      preview: "hi",
      isRead: false,
      createdAt: "2026-04-17T00:00:00.000Z",
    }

    expect(
      shouldCountNotificationAsUnreadByMode(base, { "room-1": "NO_RESTRICT" })
    ).toBe(true)
    expect(
      shouldCountNotificationAsUnreadByMode(base, { "room-1": "ONLY_MENTION" })
    ).toBe(false)
    expect(
      shouldCountNotificationAsUnreadByMode({ ...base, type: "MENTION" }, { "room-1": "ONLY_MENTION" })
    ).toBe(true)
    expect(
      shouldCountNotificationAsUnreadByMode({ ...base, type: "MENTION" }, { "room-1": "NOTHING" })
    ).toBe(false)
  })
})
