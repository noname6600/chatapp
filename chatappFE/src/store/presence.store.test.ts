// @vitest-environment jsdom

import { beforeEach, afterEach, describe, expect, it, vi } from "vitest"

import { usePresenceStore } from "./presence.store"

describe("presence.store", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.setItem("my_user_id", "me")
    usePresenceStore.getState().clearAllOnline()
    usePresenceStore.setState({ selfPresence: null })
  })

  afterEach(() => {
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
    localStorage.clear()
  })

  it("normalizes global and room presence statuses", () => {
    const store = usePresenceStore.getState()

    store.setUserStatus("u-online", "ONLINE")
    store.setUserStatus("u-away", "AWAY")
    store.setRoomPresence("room-1", [
      { userId: "u-online", status: "ONLINE" },
      { userId: "u-away", status: "AWAY" },
      { userId: "u-offline", status: "OFFLINE" },
    ])

    const nextState = usePresenceStore.getState()

    expect(nextState.getUserStatus("u-online")).toBe("ONLINE")
    expect(nextState.getUserStatus("u-away")).toBe("AWAY")
    expect(nextState.getRoomUserStatus("room-1", "u-offline")).toBe("OFFLINE")
    expect(nextState.onlineUsers["u-online"]).toBe(true)
    expect(nextState.onlineUsers["u-away"]).toBe(true)
    expect(nextState.onlineUsers["u-offline"]).toBeUndefined()
    expect(nextState.getOnlineUsersInRoom("room-1")).toEqual(["u-online", "u-away"])
  })

  it("merges global snapshots with existing live statuses", () => {
    const store = usePresenceStore.getState()

    store.setUserStatus("existing-online", "ONLINE")
    store.setGlobalPresence([{ userId: "new-away", status: "AWAY" }])

    const nextState = usePresenceStore.getState()

    expect(nextState.getUserStatus("existing-online")).toBe("ONLINE")
    expect(nextState.getUserStatus("new-away")).toBe("AWAY")
  })

  it("filters self typing and expires remote typing state", () => {
    const store = usePresenceStore.getState()

    store.setUserTyping("room-1", "me")
    expect(store.getTypingUsers("room-1")).toEqual([])

    store.setUserTyping("room-1", "remote")
    expect(store.getTypingUsers("room-1")).toEqual(["remote"])

    vi.advanceTimersByTime(6000)

    expect(usePresenceStore.getState().getTypingUsers("room-1")).toEqual([])
  })
})