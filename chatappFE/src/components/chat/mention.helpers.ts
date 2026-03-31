import type { UserProfile } from "../../types/user"
import type { MentionSuggestion } from "./MentionAutocomplete"

interface MentionTarget {
  userId: string
  token: string
}

interface RankedCandidate {
  user: UserProfile
  score: number
}

const normalize = (value: string | null | undefined): string => {
  if (!value) return ""

  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase()
}

const scoreCandidate = (displayName: string, username: string, query: string): number => {
  const display = normalize(displayName)
  const user = normalize(username)

  if (user === query || display === query) return 0
  if (user.startsWith(query)) return 1
  if (display.startsWith(query)) return 2

  const userIndex = user.indexOf(query)
  if (userIndex >= 0) return 3 + userIndex

  const displayIndex = display.indexOf(query)
  if (displayIndex >= 0) return 100 + displayIndex

  return Number.POSITIVE_INFINITY
}

export const buildMentionToken = (suggestion: MentionSuggestion): string => {
  const username = (suggestion.username || "").trim()
  if (username.length > 0) return username

  const display = (suggestion.displayName || "")
    .trim()
    .replace(/\s+/g, "_")
  if (display.length > 0) return display

  return `user_${suggestion.userId}`
}

export const filterMentionSuggestions = (
  usersById: Record<string, UserProfile>,
  candidateUserIds: string[],
  currentUserId: string | null,
  query: string,
  maxSuggestions: number
): MentionSuggestion[] => {
  const normalizedQuery = normalize(query)
  const hasQuery = normalizedQuery.length > 0

  const candidateSet = new Set(candidateUserIds)
  const hasRoomScope = candidateSet.size > 0

  const suggestions = Object.values(usersById)
    .map<RankedCandidate | null>((user) => {
      if (!user) return null
      if (hasRoomScope && !candidateSet.has(user.accountId)) return null
      if (!hasQuery && currentUserId && user.accountId === currentUserId) return null

      const displayName = user.displayName || ""
      const username = user.username || ""
      const score = hasQuery
        ? scoreCandidate(displayName, username, normalizedQuery)
        : 0

      if (!Number.isFinite(score)) return null

      return {
        user,
        score,
      }
    })
    .filter((candidate): candidate is RankedCandidate => candidate !== null)
    .sort((a, b) => {
      if (a.score !== b.score) return a.score - b.score

      const aName = normalize(a.user.displayName || a.user.username)
      const bName = normalize(b.user.displayName || b.user.username)
      return aName.localeCompare(bName)
    })
    .slice(0, maxSuggestions)
    .map(({ user }) => ({
      userId: user.accountId,
      displayName: user.displayName || user.username || user.accountId,
      username: user.username || "",
      avatarUrl: user.avatarUrl || undefined,
    }))

  return suggestions
}

export const extractMentionedUserIds = (
  content: string,
  pendingMentions: MentionTarget[]
): string[] => {
  if (!content.trim() || pendingMentions.length === 0) {
    return []
  }

  const seen = new Set<string>()

  for (const mention of pendingMentions) {
    const escaped = mention.token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
    const pattern = new RegExp(`(^|\\s)@${escaped}(?=$|\\s|[.,!?;:])`, "i")

    if (pattern.test(content)) {
      seen.add(mention.userId)
    }
  }

  return Array.from(seen)
}