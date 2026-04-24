import { useCallback, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"

import { joinRoomByInviteApi } from "../api/room.service"
import { useChat } from "../store/chat.store"
import { useRooms } from "../store/room.store"

// ─── Lifecycle ──────────────────────────────────────────────────────────────

export type InviteJoinLifecycle = "idle" | "joining" | "joined" | "failed"

export type InviteJoinFailureReason =
  | "invalid"      // room not found, forbidden, cannot join, private
  | "already-member" // user is already a member
  | "transient"    // network/server error, retry is safe
  | null

interface InviteJoinState {
  lifecycle: InviteJoinLifecycle
  failureReason: InviteJoinFailureReason
  isRetryable: boolean
}

const IDLE: InviteJoinState = {
  lifecycle: "idle",
  failureReason: null,
  isRetryable: false,
}

// ─── Error categorisation ────────────────────────────────────────────────────

export function categorizeInviteJoinError(message: string): {
  failureReason: InviteJoinFailureReason
  isRetryable: boolean
} {
  const m = message.toLowerCase()
  if (m.includes("already") || m.includes("member")) {
    return { failureReason: "already-member", isRetryable: false }
  }
  if (
    m.includes("not found") ||
    m.includes("forbidden") ||
    m.includes("cannot join") ||
    m.includes("private") ||
    m.includes("invalid") ||
    m.includes("expired")
  ) {
    return { failureReason: "invalid", isRetryable: false }
  }
  return { failureReason: "transient", isRetryable: true }
}

// ─── Hook ────────────────────────────────────────────────────────────────────

/**
 * Shared invite-join orchestrator used by both the in-app invite card and the
 * deep-link cross-tab path (`/chat?join=<roomId>`).
 *
 * Contract:
 * - API join is attempted first (HTTP-only, does not depend on websocket readiness)
 * - On success: `loadRooms()` commits membership, `setActiveRoom` navigates user in-app
 * - If the user is already a member (found in local roomsById), skips API call and
 *   navigates directly
 * - Idempotent: concurrent calls for the same roomId are coalesced via an in-flight ref
 */
export function useInviteJoin() {
  const navigate = useNavigate()
  const { loadRooms, roomsById } = useRooms()
  const { setActiveRoom } = useChat()

  const [state, setState] = useState<InviteJoinState>(IDLE)

  // Tracks which roomId is currently being joined to prevent duplicate in-flight requests.
  const inFlightRoomIdRef = useRef<string | null>(null)

  const joinRoom = useCallback(
    async (roomId: string) => {
      if (!roomId) return

      // Coalesce concurrent calls for the same room.
      if (inFlightRoomIdRef.current === roomId) return

      // If already a member in local state: skip API call and navigate directly.
      if (roomsById[roomId]) {
        await setActiveRoom(roomId)
        navigate("/chat")
        return
      }

      inFlightRoomIdRef.current = roomId
      setState({ lifecycle: "joining", failureReason: null, isRetryable: false })

      try {
        await joinRoomByInviteApi(roomId)
        await loadRooms()
        await setActiveRoom(roomId)
        navigate("/chat")
        setState({ lifecycle: "joined", failureReason: null, isRetryable: false })
      } catch (error) {
        const message = error instanceof Error ? error.message : ""
        const { failureReason, isRetryable } = categorizeInviteJoinError(message)

        // If the server says we're already a member: load rooms and navigate anyway.
        if (failureReason === "already-member") {
          try {
            await loadRooms()
            await setActiveRoom(roomId)
            navigate("/chat")
            setState({ lifecycle: "joined", failureReason: null, isRetryable: false })
            return
          } catch {
            // Fall through to failed state if recovery also fails.
          }
        }

        setState({ lifecycle: "failed", failureReason, isRetryable })
      } finally {
        inFlightRoomIdRef.current = null
      }
    },
    [loadRooms, roomsById, setActiveRoom, navigate]
  )

  const resetJoin = useCallback(() => setState(IDLE), [])

  return {
    lifecycle: state.lifecycle,
    failureReason: state.failureReason,
    isRetryable: state.isRetryable,
    joinRoom,
    resetJoin,
  }
}
