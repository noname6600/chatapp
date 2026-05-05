// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest"

import { PresenceEventType } from "../constants/presenceEvents"
import { getGlobalPresenceApi } from "../api/presence.service"
import { usePresenceStore } from "../store/presence.store"

class MockWebSocket {
  static OPEN = 1
  static instances: MockWebSocket[] = []

  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  readyState = MockWebSocket.OPEN
  sent: string[] = []

  constructor(_url: string) {
    MockWebSocket.instances.push(this)
  }

  send(data: string) {
    this.sent.push(data)
  }

  close() {
    this.readyState = 3
    this.onclose?.({} as CloseEvent)
  }
}

vi.mock("../api/presence.service", () => ({
  getGlobalPresenceApi: vi.fn(async () => []),
}))

vi.mock("../config/ws.config", () => ({
  getWsEndpoint: vi.fn(() => "ws://presence.test/ws/presence"),
}))

describe("presence.socket", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.setItem("access_token", "token")
    localStorage.setItem("my_user_id", "me")
    usePresenceStore.getState().clearAllOnline()
    MockWebSocket.instances = []
    vi.stubGlobal("WebSocket", MockWebSocket as unknown as typeof WebSocket)
  })

  it("applies websocket status and typing updates to presence store", async () => {
    const mod = await import("./presence.socket")

    mod.connectPresenceSocket()
    const ws = MockWebSocket.instances[0]

    ws.onopen?.({} as Event)

    ws.onmessage?.({
      data: JSON.stringify({
        type: PresenceEventType.USER_STATUS_CHANGED,
        payload: { userId: "user-1", status: "AWAY" },
      }),
    } as MessageEvent)

    expect(usePresenceStore.getState().getUserStatus("user-1")).toBe("AWAY")

    ws.onmessage?.({
      data: JSON.stringify({
        type: PresenceEventType.USER_TYPING,
        payload: { roomId: "room-1", userId: "user-1" },
      }),
    } as MessageEvent)

    expect(usePresenceStore.getState().getTypingUsers("room-1")).toEqual(["user-1"])

    ws.onmessage?.({
      data: JSON.stringify({
        type: PresenceEventType.USER_STOP_TYPING,
        payload: { roomId: "room-1", userId: "user-1" },
      }),
    } as MessageEvent)

    expect(usePresenceStore.getState().getTypingUsers("room-1")).toEqual([])

    mod.disconnectPresenceSocket()
  })

  it("bounds reconnect snapshot fetches under rapid reconnect churn", async () => {
    const mod = await import("./presence.socket")

    mod.connectPresenceSocket()
    const ws1 = MockWebSocket.instances[0]
    ws1.onopen?.({} as Event)

    expect(getGlobalPresenceApi).toHaveBeenCalledTimes(1)

    ws1.onclose?.({} as CloseEvent)

    // First reconnect at 1s backoff.
    vi.advanceTimersByTime(1100)
    const ws2 = MockWebSocket.instances[1]
    ws2.onopen?.({} as Event)

    // Within cooldown window, snapshot fetch should be deduped.
    expect(getGlobalPresenceApi).toHaveBeenCalledTimes(1)

    ws2.onclose?.({} as CloseEvent)
    vi.advanceTimersByTime(2000)
    const ws3 = MockWebSocket.instances[2]
    ws3.onopen?.({} as Event)

    // After cooldown, next reconnect can fetch again.
    expect(getGlobalPresenceApi).toHaveBeenCalledTimes(2)

    mod.disconnectPresenceSocket()
  })
})
