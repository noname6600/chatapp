import { useEffect, useRef } from "react"
import { useUserStore } from "../store/user.store"
import { getFriendsApi } from "../api/friend.service"
import { getRoomMembersBulk } from "../api/room.service"
import type { Room } from "../types/room"

/**
 * Populates the FE user cache with:
 * - Current user (passed in)
 * - All friends
 * - All members from all group rooms
 * 
 * This ensures senderId → displayName lookup will always succeed in room list.
 * Runs only once per userId after rooms are ready to avoid re-fetching
 * every time roomsById gets a new reference (e.g. on each incoming message).
 */
export const usePopulateUserCache = (
  userId: string | null,
  roomsById: Record<string, Room>,
  isReady: boolean
) => {
  const fetchUsers = useUserStore((state) => state.fetchUsers)

  // Keep a ref so the effect can read the latest roomsById without it being a dep
  const roomsByIdRef = useRef(roomsById)
  roomsByIdRef.current = roomsById

  // Track which userId we've already populated to avoid re-running on every room update
  const hasPopulatedRef = useRef<string | null>(null)

  useEffect(() => {
    if (!userId || !isReady) return
    if (hasPopulatedRef.current === userId) return
    hasPopulatedRef.current = userId

    const populate = async () => {
      try {
        // Step 1: Fetch and load all friends
        const friendIds = await getFriendsApi()
        if (friendIds.length > 0) {
          await fetchUsers(friendIds)
        }

        // Step 2: Collect all group member IDs via a single bulk call
        const groupRooms = Object.values(roomsByIdRef.current).filter(r => r.type === "GROUP")
        const allMemberIds = new Set<string>()

        if (groupRooms.length > 0) {
          try {
            const bulkResult = await getRoomMembersBulk(groupRooms.map(r => r.id))
            Object.values(bulkResult).flat().forEach(m => allMemberIds.add(m.userId))
          } catch (e) {
            console.warn("[user-cache] Failed to bulk-fetch group members", e)
          }
        }

        // Step 3: Batch-fetch all unique group members
        if (allMemberIds.size > 0) {
          await fetchUsers(Array.from(allMemberIds))
        }

        console.info(
          `[user-cache] Populated with ${friendIds.length} friends + ${allMemberIds.size} group members`
        )
      } catch (err) {
        console.error("[user-cache] Failed to populate", err)
      }
    }

    populate()
  }, [userId, isReady, fetchUsers])
}
