import { describe, expect, it } from "vitest"

import {
  extractMentionedUserIds,
  filterMentionSuggestions,
} from "./mention.helpers"

describe("mention helpers", () => {
  it("mention autocomplete filters current room members and matches display/username", () => {
    const suggestions = filterMentionSuggestions(
      {
        "user-1": {
          accountId: "user-1",
          username: "alice",
          displayName: "Alice Nguyen",
          avatarUrl: null,
          aboutMe: null,
          backgroundColor: null,
        },
        "user-2": {
          accountId: "user-2",
          username: "alex",
          displayName: "Alex Kim",
          avatarUrl: null,
          aboutMe: null,
          backgroundColor: null,
        },
        "user-3": {
          accountId: "user-3",
          username: "bruce",
          displayName: "Bruce Lee",
          avatarUrl: null,
          aboutMe: null,
          backgroundColor: null,
        },
        "user-4": {
          accountId: "user-4",
          username: "joana",
          displayName: "John Carter",
          avatarUrl: null,
          aboutMe: null,
          backgroundColor: null,
        },
      },
      ["user-1", "user-3", "user-4"],
      "me",
      "joan",
      5
    )

    expect(suggestions.map((item) => item.userId)).toEqual(["user-4"])
  })

  it("mention autocomplete also recommends self when query matches", () => {
    const suggestions = filterMentionSuggestions(
      {
        "user-1": {
          accountId: "user-1",
          username: "alice",
          displayName: "Alice Nguyen",
          avatarUrl: null,
          aboutMe: null,
          backgroundColor: null,
        },
        "user-2": {
          accountId: "user-2",
          username: "alex",
          displayName: "Alex Kim",
          avatarUrl: null,
          aboutMe: null,
          backgroundColor: null,
        },
      },
      ["user-1", "user-2"],
      "user-1",
      "alice",
      5
    )

    expect(suggestions.map((item) => item.userId)).toContain("user-1")
  })

  it("mention autocomplete enforces max 5 suggestions", () => {
    const users = Array.from({ length: 8 }).reduce<Record<string, any>>((acc, _, idx) => {
      const id = `user-${idx + 1}`
      acc[id] = {
        accountId: id,
        username: `alex${idx + 1}`,
        displayName: `Alex ${idx + 1}`,
        avatarUrl: null,
        aboutMe: null,
        backgroundColor: null,
      }
      return acc
    }, {})

    const candidateIds = Object.keys(users)
    const suggestions = filterMentionSuggestions(users, candidateIds, "me", "alex", 5)

    expect(suggestions).toHaveLength(5)
  })

  it("mention autocomplete returns defaults on bare trigger and excludes self", () => {
    const suggestions = filterMentionSuggestions(
      {
        "user-1": {
          accountId: "user-1",
          username: "alice",
          displayName: "Alice Nguyen",
          avatarUrl: null,
          aboutMe: null,
          backgroundColor: null,
        },
        "user-2": {
          accountId: "user-2",
          username: "alex",
          displayName: "Alex Kim",
          avatarUrl: null,
          aboutMe: null,
          backgroundColor: null,
        },
      },
      ["user-1", "user-2"],
      "user-1",
      "",
      5
    )

    expect(suggestions.map((item) => item.userId)).toEqual(["user-2"])
  })

  it("send payload includes mentionedUserIds for present mentions", () => {
    const mentionedUserIds = extractMentionedUserIds(
      "hello @alice and @bruce",
      [
        { userId: "user-1", token: "alice" },
        { userId: "user-3", token: "bruce" },
        { userId: "user-x", token: "not-present" },
      ]
    )

    expect(mentionedUserIds).toEqual(["user-1", "user-3"])
  })
})
