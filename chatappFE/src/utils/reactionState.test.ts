import { describe, expect, it } from "vitest"

import { applyReactionEvent, toggleReactionLocally } from "./reactionState"

describe("reactionState", () => {
  it("toggle on same emoji adds then removes reaction", () => {
    const afterAdd = toggleReactionLocally([], "👍")
    expect(afterAdd).toEqual([{ emoji: "👍", count: 1, reactedByMe: true }])

    const afterRemove = toggleReactionLocally(afterAdd, "👍")
    expect(afterRemove).toEqual([])
  })

  it("rapid optimistic toggle + realtime add/remove remains idempotent", () => {
    const mine = "me"

    const optimisticAdd = toggleReactionLocally([], "🔥")
    const eventAdd = applyReactionEvent(
      optimisticAdd,
      { emoji: "🔥", userId: mine, action: "ADD" },
      mine
    )

    expect(eventAdd).toEqual([{ emoji: "🔥", count: 1, reactedByMe: true }])

    const optimisticRemove = toggleReactionLocally(eventAdd, "🔥")
    const eventRemove = applyReactionEvent(
      optimisticRemove,
      { emoji: "🔥", userId: mine, action: "REMOVE" },
      mine
    )

    expect(eventRemove).toEqual([])
  })

  it("applyReactionEvent supports other user add/remove transitions", () => {
    const base = [{ emoji: "😀", count: 1, reactedByMe: true }]

    const withOther = applyReactionEvent(
      base,
      { emoji: "😀", userId: "user-b", action: "ADD" },
      "me"
    )
    expect(withOther).toEqual([{ emoji: "😀", count: 2, reactedByMe: true }])

    const otherRemoved = applyReactionEvent(
      withOther,
      { emoji: "😀", userId: "user-b", action: "REMOVE" },
      "me"
    )
    expect(otherRemoved).toEqual([{ emoji: "😀", count: 1, reactedByMe: true }])
  })

  it("reaction merge preserves unrelated message fields", () => {
    const message = {
      messageId: "m1",
      roomId: "r1",
      senderId: "user-a",
      seq: 99,
      type: "TEXT",
      content: "hello",
      createdAt: "2026-03-20T10:00:00Z",
      attachments: [{ id: "a1", type: "FILE", url: "u" }],
      reactions: [{ emoji: "🔥", count: 1, reactedByMe: true }],
    }

    const next = {
      ...message,
      reactions: applyReactionEvent(
        message.reactions,
        { emoji: "🔥", userId: "user-b", action: "ADD" },
        "user-a"
      ),
    }

    expect(next.messageId).toBe(message.messageId)
    expect(next.senderId).toBe(message.senderId)
    expect(next.content).toBe(message.content)
    expect(next.createdAt).toBe(message.createdAt)
    expect(next.attachments).toEqual(message.attachments)
    expect(next.reactions).toEqual([{ emoji: "🔥", count: 2, reactedByMe: true }])
  })
})
