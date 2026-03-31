import { describe, it, expect } from "vitest"
import { deduplicateReactions, normalizeReactions } from "./reactionState"
import type { Reaction } from "../types/message"

describe("deduplicateReactions", () => {
  it("should return empty array for empty input", () => {
    expect(deduplicateReactions([])).toEqual([])
  })

  it("should return input unchanged if no duplicates", () => {
    const reactions: Reaction[] = [
      { emoji: "🔥", count: 2, reactedByMe: true },
      { emoji: "❤️", count: 1, reactedByMe: false },
      { emoji: "👍", count: 3, reactedByMe: false },
    ]
    expect(deduplicateReactions(reactions)).toEqual(reactions)
  })

  it("should deduplicate single duplicate reaction by emoji", () => {
    const reactions: Reaction[] = [
      { emoji: "🔥", count: 1, reactedByMe: false },
      { emoji: "🔥", count: 2, reactedByMe: true },
    ]
    const result = deduplicateReactions(reactions)

    expect(result).toHaveLength(1)
    expect(result[0].emoji).toBe("🔥")
    expect(result[0].count).toBe(2) // Keeps highest count
    expect(result[0].reactedByMe).toBe(true) // Merges reactedByMe
  })

  it("should merge reactedByMe when consolidating duplicates", () => {
    const reactions: Reaction[] = [
      { emoji: "🔥", count: 2, reactedByMe: false },
      { emoji: "🔥", count: 1, reactedByMe: true },
    ]
    const result = deduplicateReactions(reactions)

    expect(result).toHaveLength(1)
    expect(result[0].reactedByMe).toBe(true) // reactedByMe from second is merged
  })

  it("should handle multiple duplicates from the same user", () => {
    const reactions: Reaction[] = [
      { emoji: "🔥", count: 1, reactedByMe: true },
      { emoji: "❤️", count: 1, reactedByMe: false },
      { emoji: "🔥", count: 2, reactedByMe: true },
      { emoji: "🔥", count: 3, reactedByMe: true },
      { emoji: "❤️", count: 2, reactedByMe: false },
    ]
    const result = deduplicateReactions(reactions)

    expect(result).toHaveLength(2)
    
    const fire = result.find((r) => r.emoji === "🔥")
    const heart = result.find((r) => r.emoji === "❤️")
    
    expect(fire).toEqual({ emoji: "🔥", count: 3, reactedByMe: true })
    expect(heart).toEqual({ emoji: "❤️", count: 2, reactedByMe: false })
  })

  it("should prefer backend reaction over optimistic on count conflict", () => {
    // Scenario: optimistic added emoji with count 1, backend has count 5
    const reactions: Reaction[] = [
      { emoji: "🔥", count: 1, reactedByMe: true }, // Optimistic
      { emoji: "🔥", count: 5, reactedByMe: false }, // Real (less recent, but higher count)
    ]
    const result = deduplicateReactions(reactions)

    expect(result).toHaveLength(1)
    expect(result[0].count).toBe(5) // Higher count indicates more accurate
  })

  it("should handle null/undefined gracefully", () => {
    expect(deduplicateReactions(null as any)).toEqual(null)
    expect(deduplicateReactions(undefined as any)).toEqual(undefined)
  })

  it("should preserve insertion order after dedup", () => {
    const reactions: Reaction[] = [
      { emoji: "🔥", count: 1, reactedByMe: false },
      { emoji: "❤️", count: 1, reactedByMe: false },
      { emoji: "👍", count: 1, reactedByMe: false },
      { emoji: "🔥", count: 2, reactedByMe: true },
    ]
    const result = deduplicateReactions(reactions)

    // Order should be first-seen: 🔥, ❤️, 👍
    expect(result.map((r) => r.emoji)).toEqual(["🔥", "❤️", "👍"])
  })
})

describe("normalizeReactions", () => {
  it("maps ownership aliases to reactedByMe", () => {
    const input = [
      { emoji: "🔥", count: 2, reacted_by_me: true },
      { emoji: "❤️", count: 1, isMine: false },
    ] as any

    const result = normalizeReactions(input)

    expect(result).toEqual([
      { emoji: "🔥", count: 2, reactedByMe: true },
      { emoji: "❤️", count: 1, reactedByMe: false },
    ])
  })

  it("keeps ownership undefined when backend does not provide it", () => {
    const result = normalizeReactions([{ emoji: "👍", count: 3 }] as any)
    expect(result).toEqual([{ emoji: "👍", count: 3 }])
  })
})
