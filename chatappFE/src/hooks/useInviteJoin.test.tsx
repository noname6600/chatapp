// @vitest-environment jsdom

import { describe, expect, it, vi, beforeEach } from "vitest"
import { act, renderHook, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import type { ReactNode } from "react"

import { categorizeInviteJoinError, useInviteJoin } from "./useInviteJoin"

// ─── Mock dependencies ────────────────────────────────────────────────────────

const mockLoadRooms = vi.fn()
const mockSetActiveRoom = vi.fn()
const mockNavigate = vi.fn()

vi.mock("../api/room.service", () => ({
  joinRoomByInviteApi: vi.fn(),
}))

vi.mock("../store/chat.store", () => ({
  useChat: () => ({ setActiveRoom: mockSetActiveRoom }),
}))

vi.mock("../store/room.store", () => ({
  useRooms: () => ({ loadRooms: mockLoadRooms, roomsById: {} }),
}))

vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>()
  return { ...actual, useNavigate: () => mockNavigate }
})

import { joinRoomByInviteApi } from "../api/room.service"

// ─── Test wrapper ─────────────────────────────────────────────────────────────

function wrapper({ children }: { children: ReactNode }) {
  return <MemoryRouter>{children}</MemoryRouter>
}

// ─── categorizeInviteJoinError ────────────────────────────────────────────────

describe("categorizeInviteJoinError", () => {
  it.each([
    ["not found", "invalid", false],
    ["forbidden", "invalid", false],
    ["cannot join this group", "invalid", false],
    ["private room", "invalid", false],
    ["invalid invite", "invalid", false],
    ["link expired", "invalid", false],
    ["already a member", "already-member", false],
    ["user is member", "already-member", false],
    ["network error", "transient", true],
    ["500 internal server error", "transient", true],
    ["", "transient", true],
  ])("maps '%s' → { failureReason: '%s', isRetryable: %s }", (msg, reason, retryable) => {
    const result = categorizeInviteJoinError(msg)
    expect(result.failureReason).toBe(reason)
    expect(result.isRetryable).toBe(retryable)
  })
})

// ─── useInviteJoin ────────────────────────────────────────────────────────────

describe("useInviteJoin", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLoadRooms.mockResolvedValue(undefined)
    mockSetActiveRoom.mockResolvedValue(undefined)
  })

  it("starts in idle state", () => {
    const { result } = renderHook(() => useInviteJoin(), { wrapper })
    expect(result.current.lifecycle).toBe("idle")
    expect(result.current.failureReason).toBeNull()
    expect(result.current.isRetryable).toBe(false)
  })

  it("transitions to joined on successful join: idle → joining → joined", async () => {
    vi.mocked(joinRoomByInviteApi).mockResolvedValue(undefined)

    const { result } = renderHook(() => useInviteJoin(), { wrapper })

    act(() => {
      void result.current.joinRoom("room-1")
    })

    // Should immediately be in joining state
    expect(result.current.lifecycle).toBe("joining")

    await waitFor(() => {
      expect(result.current.lifecycle).toBe("joined")
    })

    expect(joinRoomByInviteApi).toHaveBeenCalledWith("room-1")
    expect(mockLoadRooms).toHaveBeenCalled()
    expect(mockSetActiveRoom).toHaveBeenCalledWith("room-1")
    expect(mockNavigate).toHaveBeenCalledWith("/chat")
  })

  it("transitions to failed with failureReason=invalid for a 404-style error", async () => {
    vi.mocked(joinRoomByInviteApi).mockRejectedValue(new Error("Room not found"))

    const { result } = renderHook(() => useInviteJoin(), { wrapper })

    act(() => {
      void result.current.joinRoom("room-bad")
    })

    await waitFor(() => {
      expect(result.current.lifecycle).toBe("failed")
    })

    expect(result.current.failureReason).toBe("invalid")
    expect(result.current.isRetryable).toBe(false)
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it("transitions to failed with failureReason=transient for a network error", async () => {
    vi.mocked(joinRoomByInviteApi).mockRejectedValue(new Error("network error"))

    const { result } = renderHook(() => useInviteJoin(), { wrapper })

    act(() => {
      void result.current.joinRoom("room-net")
    })

    await waitFor(() => {
      expect(result.current.lifecycle).toBe("failed")
    })

    expect(result.current.failureReason).toBe("transient")
    expect(result.current.isRetryable).toBe(true)
  })

  it("recovers gracefully when server says already-member: navigates without error", async () => {
    vi.mocked(joinRoomByInviteApi).mockRejectedValue(new Error("already a member"))

    const { result } = renderHook(() => useInviteJoin(), { wrapper })

    act(() => {
      void result.current.joinRoom("room-existing")
    })

    await waitFor(() => {
      expect(result.current.lifecycle).toBe("joined")
    })

    expect(mockLoadRooms).toHaveBeenCalled()
    expect(mockSetActiveRoom).toHaveBeenCalledWith("room-existing")
    expect(mockNavigate).toHaveBeenCalledWith("/chat")
  })

  it("skips API call and navigates directly when room already in roomsById", async () => {
    vi.mock("../store/room.store", () => ({
      useRooms: () => ({
        loadRooms: mockLoadRooms,
        roomsById: { "room-known": { id: "room-known", name: "Known Room" } },
      }),
    }))

    // Re-import after mock update — use a fresh module for this test
    const { useInviteJoin: useInviteJoinFresh } = await import("./useInviteJoin")
    const { result } = renderHook(() => useInviteJoinFresh(), { wrapper })

    act(() => {
      void result.current.joinRoom("room-known")
    })

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/chat")
    })

    expect(joinRoomByInviteApi).not.toHaveBeenCalled()
  })

  it("coalesces concurrent calls for the same roomId (idempotent)", async () => {
    let resolveJoin!: () => void
    vi.mocked(joinRoomByInviteApi).mockReturnValue(
      new Promise<void>((resolve) => { resolveJoin = resolve })
    )

    const { result } = renderHook(() => useInviteJoin(), { wrapper })

    act(() => {
      // Fire twice in the same tick
      void result.current.joinRoom("room-dup")
      void result.current.joinRoom("room-dup")
    })

    act(() => resolveJoin())

    await waitFor(() => {
      expect(result.current.lifecycle).toBe("joined")
    })

    // API should have been called exactly once despite two concurrent calls
    expect(joinRoomByInviteApi).toHaveBeenCalledTimes(1)
  })

  it("resetJoin returns state to idle", async () => {
    vi.mocked(joinRoomByInviteApi).mockRejectedValue(new Error("network error"))

    const { result } = renderHook(() => useInviteJoin(), { wrapper })

    act(() => {
      void result.current.joinRoom("room-fail")
    })

    await waitFor(() => {
      expect(result.current.lifecycle).toBe("failed")
    })

    act(() => {
      result.current.resetJoin()
    })

    expect(result.current.lifecycle).toBe("idle")
    expect(result.current.failureReason).toBeNull()
  })
})
