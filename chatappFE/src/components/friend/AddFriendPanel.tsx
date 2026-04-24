import { useEffect, useMemo, useRef, useState } from "react"
import { sendFriendRequestApi } from "../../api/friend.service"
import { searchUserByUsernameApi } from "../../api/user.service"
import { useFriendStatus } from "../../hooks/useFriendStatus"
import { useFriendStore } from "../../store/friend.store"
import { useUserOverlay } from "../../store/userOverlay.store"
import { useUserStore } from "../../store/user.store"
import type { FriendStatus } from "../../types/friend"
import type { UserProfile } from "../../types/user"

type SearchState = "idle" | "loading" | "success" | "empty" | "error"

const SEARCH_DEBOUNCE_MS = 600
const MIN_QUERY_LENGTH = 2
const MAX_VISIBLE_RESULTS = 10

const toUserProfile = (profile: UserProfile): UserProfile => ({
  accountId: profile.accountId,
  username: profile.username,
  displayName: profile.displayName,
  avatarUrl: profile.avatarUrl,
  aboutMe: profile.aboutMe ?? null,
  backgroundColor: profile.backgroundColor ?? null,
})

const getActionCopy = (
  friendStatus: FriendStatus | undefined,
  isSending: boolean
): { label: string; helper: string; disabled: boolean } => {
  if (isSending) {
    return {
      label: "Sending...",
      helper: "Sending friend request...",
      disabled: true,
    }
  }

  switch (friendStatus) {
    case "SELF":
      return {
        label: "This is you",
        helper: "You cannot send a friend request to your own profile.",
        disabled: true,
      }
    case "FRIENDS":
      return {
        label: "Friends",
        helper: "You are already connected with this user.",
        disabled: true,
      }
    case "REQUEST_SENT":
      return {
        label: "Pending",
        helper: "A friend request is already waiting for this user.",
        disabled: true,
      }
    case "REQUEST_RECEIVED":
      return {
        label: "Request received",
        helper: "This user already sent you a friend request. Check the Pending tab.",
        disabled: true,
      }
    case "BLOCKED_BY_ME":
    case "BLOCKED_ME":
      return {
        label: "Blocked",
        helper: "Friend requests are unavailable while this user is blocked.",
        disabled: true,
      }
    case "NONE":
      return {
        label: "Add Friend",
        helper: "Click the card to inspect the profile, or send the request directly here.",
        disabled: false,
      }
    default:
      return {
        label: "Checking...",
        helper: "Checking your current friendship status...",
        disabled: true,
      }
  }
}

const getStatusLabel = (friendStatus: FriendStatus | undefined): string => {
  switch (friendStatus) {
    case "SELF":
      return "You"
    case "FRIENDS":
      return "Friends"
    case "REQUEST_SENT":
      return "Pending"
    case "REQUEST_RECEIVED":
      return "Request received"
    case "BLOCKED_BY_ME":
    case "BLOCKED_ME":
      return "Blocked"
    case "NONE":
      return "Not friends"
    default:
      return "Checking..."
  }
}

type FriendSearchCardProps = {
  user: UserProfile
  isSending: boolean
  onOpenProfile: (user: UserProfile, rect: DOMRect) => void
  onAddFriend: (user: UserProfile) => void
}

const FriendSearchCard = ({
  user,
  isSending,
  onOpenProfile,
  onAddFriend,
}: FriendSearchCardProps) => {
  const cardRef = useRef<HTMLDivElement>(null)
  const friendStatus = useFriendStatus(user.accountId)
  const actionCopy = useMemo(
    () => getActionCopy(friendStatus, isSending),
    [friendStatus, isSending]
  )

  const handleOpenProfile = () => {
    if (!cardRef.current) return
    onOpenProfile(user, cardRef.current.getBoundingClientRect())
  }

  return (
    <div
      ref={cardRef}
      role="button"
      tabIndex={0}
      aria-label={`Open profile for ${user.displayName}`}
      onClick={handleOpenProfile}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault()
          handleOpenProfile()
        }
      }}
      className="flex items-center justify-between gap-4 rounded-lg border border-gray-200 bg-white px-4 py-3 text-left transition hover:border-blue-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
      data-testid="friend-search-card"
    >
      <div className="flex items-center gap-3">
        <img
          src={user.avatarUrl ?? "/default-avatar.png"}
          alt={user.displayName}
          className="h-12 w-12 rounded-full object-cover"
        />
        <div className="space-y-1">
          <div className="text-sm font-semibold text-gray-900">
            {user.displayName}
          </div>
          <div className="text-xs text-gray-500">@{user.username}</div>
          <div className="text-xs text-gray-400">{getStatusLabel(friendStatus)}</div>
        </div>
      </div>

      <button
        type="button"
        onClick={(event) => {
          event.stopPropagation()
          if (!actionCopy.disabled) {
            onAddFriend(user)
          }
        }}
        disabled={actionCopy.disabled}
        className="rounded-md bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300 disabled:text-gray-700"
      >
        {actionCopy.label}
      </button>
    </div>
  )
}

export function AddFriendPanel() {
  const updateUserLocal = useUserStore((state) => state.updateUserLocal)
  const openUserOverlay = useUserOverlay((state) => state.open)
  const setFriendStatus = useFriendStore((state) => state.setStatus)

  const [searchText, setSearchText] = useState("")
  const [searchState, setSearchState] = useState<SearchState>("idle")
  const [results, setResults] = useState<UserProfile[]>([])
  const [sendingRequestId, setSendingRequestId] = useState<string | null>(null)

  useEffect(() => {
    const normalized = searchText.trim()
    setSendingRequestId(null)

    if (!normalized || normalized.length < MIN_QUERY_LENGTH) {
      setResults([])
      setSearchState("idle")
      return
    }

    let cancelled = false
    const timeoutId = window.setTimeout(() => {
      void (async () => {
        try {
          setSearchState("loading")

          const matches = await searchUserByUsernameApi(normalized)
          if (cancelled) return

          const hydratedMatches = matches
            .slice(0, MAX_VISIBLE_RESULTS)
            .map((match) =>
            toUserProfile({
              ...match,
              aboutMe: match.aboutMe ?? null,
              backgroundColor: match.backgroundColor ?? null,
            })
          )

          hydratedMatches.forEach(updateUserLocal)

          setResults(hydratedMatches)
          if (hydratedMatches.length === 0) {
            setSearchState("empty")
            return
          }

          setSearchState("success")
        } catch (error) {
          if (cancelled) return
          console.error("Failed to search username", error)
          setResults([])
          setSearchState("error")
        }
      })()
    }, SEARCH_DEBOUNCE_MS)

    return () => {
      cancelled = true
      window.clearTimeout(timeoutId)
    }
  }, [searchText, updateUserLocal])

  const handleOpenProfile = (user: UserProfile, rect: DOMRect) => {
    openUserOverlay(user.accountId, rect, "FRIEND_SEARCH")
  }

  const handleAddFriend = async (user: UserProfile) => {
    if (!user || sendingRequestId) return

    try {
      setSendingRequestId(user.accountId)
      await sendFriendRequestApi(user.accountId)
      setFriendStatus(user.accountId, "REQUEST_SENT")
      setSendingRequestId(null)
    } catch (error) {
      console.error("Failed to send friend request", error)
      setSendingRequestId(null)
    }
  }

  return (
    <div className="flex h-full min-h-0 flex-col space-y-6 rounded-xl border border-gray-200 bg-gray-50 p-4">
      <section className="space-y-3">
        <div className="space-y-1">
          <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-600">
            Search people
          </h3>
          <p className="text-sm text-gray-500">
            Type at least {MIN_QUERY_LENGTH} characters and pause to see matches.
          </p>
        </div>

        <input
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          placeholder="Search by username"
          className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500"
        />

        {searchState === "loading" ? (
          <p className="text-sm text-gray-500">Searching for matches...</p>
        ) : null}

        {searchState === "empty" ? (
          <p className="text-sm text-amber-600">No matches found yet.</p>
        ) : null}

        {searchState === "error" ? (
          <p className="text-sm text-red-600">
            We couldn&apos;t search right now. Please try again.
          </p>
        ) : null}
      </section>

      {results.length > 0 ? (
        <section className="flex min-h-0 flex-1 flex-col space-y-3">
          <div className="space-y-1">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-600">
              Results
            </h3>
            <p className="text-sm text-gray-500">
              Click a card to preview the profile or send a request.
            </p>
          </div>

          <div className="min-h-0 flex-1 space-y-2 overflow-y-auto pr-1 max-h-[45vh]">
            {results.map((user) => (
              <FriendSearchCard
                key={user.accountId}
                user={user}
                isSending={sendingRequestId === user.accountId}
                onOpenProfile={handleOpenProfile}
                onAddFriend={handleAddFriend}
              />
            ))}
          </div>
        </section>
      ) : null}
    </div>
  )
}
