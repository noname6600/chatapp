import type { Reaction } from "../types/message"

type ReactionAction = "ADD" | "REMOVE"

type UnknownReaction = Reaction & Record<string, unknown>

/**
 * Utility function to clone an array of reactions.
 * Creates a shallow copy of each reaction object.
 */
function clone(reactions: Reaction[]) {
  return reactions.map((r) => ({ ...r }))
}

function toOptionalBoolean(value: unknown): boolean | undefined {
  if (typeof value === "boolean") {
    return value
  }
  return undefined
}

/**
 * Normalizes backend reaction payloads to the frontend Reaction shape.
 * Supports common ownership aliases used by different APIs.
 */
export function normalizeReaction(input: UnknownReaction): Reaction {
  const reactedByMe =
    toOptionalBoolean(input.reactedByMe) ??
    toOptionalBoolean(input.reacted_by_me) ??
    toOptionalBoolean(input.reactedByCurrentUser) ??
    toOptionalBoolean(input.currentUserReacted) ??
    toOptionalBoolean(input.isMine) ??
    toOptionalBoolean(input.mine)

  return {
    emoji: String(input.emoji ?? ""),
    count: Number(input.count ?? 0),
    ...(reactedByMe !== undefined ? { reactedByMe } : {}),
  }
}

export function normalizeReactions(reactions: Reaction[] = []): Reaction[] {
  return reactions
    .map((reaction) => normalizeReaction(reaction as UnknownReaction))
    .filter((reaction) => reaction.emoji.length > 0)
}

/**
 * Toggles a reaction locally (for optimistic UI update).
 *
 * Logic:
 * - If user hasn't reacted with this emoji: adds reaction with count=1, reactedByMe=true
 * - If user has reacted with this emoji: decrements count, sets reactedByMe=false
 * - If count reaches 0, removes the reaction entirely
 *
 * @param reactions - Current array of reactions (may be empty)
 * @param emoji - The emoji to toggle
 * @returns New reactions array with the emoji toggled
 *
 * @example
 * const current = [{ emoji: "👍", count: 2, reactedByMe: false }]
 * const toggled = toggleReactionLocally(current, "👍")
 * // Result: [{ emoji: "👍", count: 3, reactedByMe: true }]
 */
export function toggleReactionLocally(
  reactions: Reaction[],
  emoji: string
): Reaction[] {
  const next = clone(reactions)
  const idx = next.findIndex((r) => r.emoji === emoji)

  if (idx === -1) {
    next.push({ emoji, count: 1, reactedByMe: true })
    return next
  }

  const target = { ...next[idx] }

  if (target.reactedByMe) {
    target.count = Math.max(0, target.count - 1)
    target.reactedByMe = false

    if (target.count === 0) {
      next.splice(idx, 1)
      return next
    }

    next[idx] = target
    return next
  }

  target.count += 1
  target.reactedByMe = true
  next[idx] = target
  return next
}

/**
 * Applies a reaction event from WebSocket to the local reactions array.
 *
 * Handles:
 * - ADD/REMOVE actions from other users or current user
 * - Avoids doubling count if optimistic update was already applied
 * - Sets reactedByMe=true only if the event is from current user
 *
 * @param reactions - Current reactions array
 * @param payload - WebSocket event payload with emoji, userId, action
 * @param currentUserId - ID of current user (for identifying own reactions)
 * @returns New reactions array with event applied
 *
 * @example
 * const event = { emoji: "❤️", userId: "user2", action: "ADD" }
 * const updated = applyReactionEvent(reactions, event, "user1")
 * // reactedByMe will be false since event is from user2
 */
export function applyReactionEvent(
  reactions: Reaction[],
  payload: {
    emoji: string
    userId: string
    action: string
  },
  currentUserId: string | null
): Reaction[] {
  const next = clone(reactions)
  const idx = next.findIndex((r) => r.emoji === payload.emoji)
  const isMe = !!currentUserId && payload.userId === currentUserId
  const action = payload.action.toUpperCase() as ReactionAction

  if (action === "ADD") {
    if (idx === -1) {
      next.push({
        emoji: payload.emoji,
        count: 1,
        reactedByMe: isMe,
      })
      return next
    }

    const target = { ...next[idx] }

    // Avoid double increment when local optimistic update already applied.
    if (isMe && target.reactedByMe) {
      return next
    }

    target.count += 1
    if (isMe) {
      target.reactedByMe = true
    }
    next[idx] = target
    return next
  }

  if (action === "REMOVE") {
    if (idx === -1) {
      return next
    }

    const target = { ...next[idx] }

    // Avoid double decrement only when local optimistic REMOVE already applied.
    // Important: backend history can omit reactedByMe, so undefined must NOT be treated as false.
    if (isMe && target.reactedByMe === false) {
      return next
    }

    target.count = Math.max(0, target.count - 1)
    if (isMe) {
      target.reactedByMe = false
    }

    if (target.count === 0) {
      next.splice(idx, 1)
      return next
    }

    next[idx] = target
    return next
  }

  return next
}

/**
 * Deduplicates reactions array by emoji, keeping only one entry per emoji.
 * If multiple entries for the same emoji exist (edge case), keeps the one with highest count
 * and merges reactedByMe state (true if any of them had it as true).
 *
 * @param reactions - Array of reactions that may contain duplicates
 * @returns Deduplicated reactions array
 */
export function deduplicateReactions(reactions: Reaction[]): Reaction[] {
  if (!reactions || reactions.length === 0) {
    return reactions
  }

  const seen = new Map<string, Reaction>()

  for (const reaction of reactions) {
    const existing = seen.get(reaction.emoji)

    if (!existing) {
      // First time seeing this emoji
      seen.set(reaction.emoji, { ...reaction })
    } else {
      // Duplicate emoji found - merge by keeping highest count and unifying reactedByMe
      if (reaction.count > existing.count) {
        existing.count = reaction.count
      }
      // If either has reactedByMe, the merged result should have it as true
      if (reaction.reactedByMe) {
        existing.reactedByMe = true
      }
    }
  }

  return Array.from(seen.values())
}
