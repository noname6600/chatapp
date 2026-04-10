import { useEffect, useMemo, useState } from "react"
import {
  getFriendsApi,
  getOutgoingApi,
  sendFriendRequestApi,
  sendFriendRequestByUsernameApi,
} from "../../api/friend.service"
import { getRoomMembersBulk } from "../../api/room.service"
import { useAuth } from "../../store/auth.store"
import { useRooms } from "../../store/room.store"
import { useUserStore } from "../../store/user.store"
import { useFriendStatus } from "../../hooks/useFriendStatus"
import { onFriendshipEvent, FriendshipEventType } from "../../websocket/friendship.socket"
import type { UserProfile } from "../../types/user"

function SuggestionCard({
  user,
  pending,
  onAdd,
}: {
  user: UserProfile
  pending: boolean
  onAdd: (userId: string) => Promise<void>
}) {
  const friendStatus = useFriendStatus(user.accountId)

  const disabled =
    pending ||
    friendStatus === "FRIENDS" ||
    friendStatus === "REQUEST_SENT" ||
    friendStatus === "BLOCKED_BY_ME" ||
    friendStatus === "BLOCKED_ME"

  const label = (() => {
    if (pending || friendStatus === "REQUEST_SENT") return "Pending"
    if (friendStatus === "FRIENDS") return "Friends"
    if (friendStatus === "BLOCKED_BY_ME") return "Blocked"
    if (friendStatus === "BLOCKED_ME") return "Blocked"
    return "Add"
  })()

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="flex items-center gap-3">
        <img
          src={user.avatarUrl ?? "https://via.placeholder.com/40"}
          alt={user.displayName}
          className="h-10 w-10 rounded-full object-cover"
        />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-gray-900">{user.displayName}</p>
          <p className="truncate text-xs text-gray-500">@{user.username}</p>
        </div>
      </div>

      <button
        onClick={() => void onAdd(user.accountId)}
        disabled={disabled}
        className="mt-3 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {label}
      </button>
    </div>
  )
}

export function AddFriendPanel() {
  const { userId: currentUserId } = useAuth()
  const { roomsById } = useRooms()
  const users = useUserStore((state) => state.users)
  const fetchUsers = useUserStore((state) => state.fetchUsers)

  const [friends, setFriends] = useState<string[]>([])
  const [outgoing, setOutgoing] = useState<string[]>([])
  const [candidateIds, setCandidateIds] = useState<string[]>([])
  const [pendingSuggestionIds, setPendingSuggestionIds] = useState<Record<string, true>>({})

  const [searchText, setSearchText] = useState("")
  const [searchLoading, setSearchLoading] = useState(false)

  useEffect(() => {
    let active = true

    const loadFriendLists = async () => {
      try {
        const [friendIds, outgoingIds] = await Promise.all([getFriendsApi(), getOutgoingApi()])
        if (!active) return

        setFriends(friendIds)
        setOutgoing(outgoingIds)
      } catch (err) {
        console.error("Failed to load friend relationship lists", err)
      }
    }

    void loadFriendLists()

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true

    const loadCandidates = async () => {
      const groupRoomIds = Object.values(roomsById)
        .filter((room) => room.type === "GROUP")
        .map((room) => room.id)

      if (!groupRoomIds.length) {
        setCandidateIds([])
        return
      }

      try {
        const membersByRoom = await getRoomMembersBulk(groupRoomIds)
        if (!active) return

        const allIds = new Set<string>()
        Object.values(membersByRoom).forEach((members) => {
          members.forEach((member) => {
            if (member.userId && member.userId !== currentUserId) {
              allIds.add(member.userId)
            }
          })
        })

        const ids = Array.from(allIds)
        setCandidateIds(ids)
        if (ids.length) {
          await fetchUsers(ids)
        }
      } catch (err) {
        console.warn("Failed to load suggestion candidates", err)
      }
    }

    void loadCandidates()

    return () => {
      active = false
    }
  }, [roomsById, currentUserId, fetchUsers])

  // Handle realtime friend request status updates for recommendation cards
  useEffect(() => {
    const unsubscribe = onFriendshipEvent((event) => {
      switch (event.type) {
        case FriendshipEventType.FRIEND_REQUEST_ACCEPTED: {
          // Move user from outgoing to friends list
          const targetUserId = event.data.senderId || event.data.userId
          if (targetUserId) {
            setOutgoing((prev) => prev.filter((id) => id !== targetUserId))
            setFriends((prev) => (prev.includes(targetUserId) ? prev : [...prev, targetUserId]))
            setPendingSuggestionIds((prev) => {
              const next = { ...prev }
              delete next[targetUserId]
              return next
            })
          }
          break
        }

        case FriendshipEventType.FRIEND_REQUEST_DECLINED:
        case FriendshipEventType.FRIEND_REQUEST_CANCELLED: {
          // Remove user from outgoing list (back to available)
          const targetUserId = event.data.senderId || event.data.userId
          if (targetUserId) {
            setOutgoing((prev) => prev.filter((id) => id !== targetUserId))
            setPendingSuggestionIds((prev) => {
              const next = { ...prev }
              delete next[targetUserId]
              return next
            })
          }
          break
        }

        // Ignore other events for add-friend context
        case FriendshipEventType.FRIEND_REQUEST_RECEIVED:
        case FriendshipEventType.FRIEND_STATUS_CHANGED:
        default:
          break
      }
    })

    return () => {
      unsubscribe()
    }
  }, [])

  const suggestions = useMemo(() => {
    const friendSet = new Set(friends)
    const outgoingSet = new Set(outgoing)

    return candidateIds
      .filter((id) => id !== currentUserId)
      .filter((id) => !friendSet.has(id))
      .filter((id) => !outgoingSet.has(id))
      .map((id) => users[id])
      .filter((profile): profile is UserProfile => Boolean(profile))
      .slice(0, 20)
  }, [candidateIds, currentUserId, friends, outgoing, users])

  const sendByUsername = async () => {
    const raw = searchText.trim()
    if (!raw || searchLoading) return

    try {
      setSearchLoading(true)
      await sendFriendRequestByUsernameApi(raw)
      setSearchText("")
    } catch (err) {
      console.error(err)
    } finally {
      setSearchLoading(false)
    }
  }

  const sendFromSuggestion = async (userId: string) => {
    try {
      await sendFriendRequestApi(userId)
      setPendingSuggestionIds((prev) => ({ ...prev, [userId]: true }))
      setOutgoing((prev) => (prev.includes(userId) ? prev : [...prev, userId]))
    } catch (err) {
      console.error(err)
    }
  }

  return (
    <div className="space-y-6 rounded-xl border border-gray-200 bg-gray-50 p-5">
      <section className="space-y-3">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-600">Search by username</h3>
        <div className="flex gap-2">
          <input
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                void sendByUsername()
              }
            }}
            placeholder="Enter exact username"
            className="flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500"
          />
          <button
            onClick={() => void sendByUsername()}
            disabled={!searchText.trim() || searchLoading}
            className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {searchLoading ? "Sending..." : "Send Request"}
          </button>
        </div>
      </section>

      {suggestions.length > 0 && (
        <section className="space-y-3">
          <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-600">People you may know</h3>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {suggestions.map((user) => (
              <SuggestionCard
                key={user.accountId}
                user={user}
                pending={Boolean(pendingSuggestionIds[user.accountId])}
                onAdd={sendFromSuggestion}
              />
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
