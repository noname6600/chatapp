// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest"

class MockWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3
  static instances: MockWebSocket[] = []

  readonly url: string
  readyState = MockWebSocket.CONNECTING
  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }

  close() {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.(new CloseEvent("close", { code: 1006 }))
  }

  open() {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.(new Event("open"))
  }

  failBeforeOpen() {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.(new CloseEvent("close", { code: 1006 }))
  }

  /**
   * Simulate a rapid close after open (within stability window)
   */
  rapidPostOpenClose() {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.(new Event("open"))
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.(new CloseEvent("close", { code: 1006 }))
  }
}

describe("notification.socket", () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    vi.useFakeTimers()
    MockWebSocket.instances = []
    vi.spyOn(console, "warn").mockImplementation(() => {})
    vi.spyOn(console, "error").mockImplementation(() => {})
    vi.spyOn(console, "log").mockImplementation(() => {})
    vi.spyOn(console, "debug").mockImplementation(() => {})
    localStorage.clear()
    localStorage.setItem("access_token", "token-123")
    vi.stubGlobal("WebSocket", MockWebSocket)
  })

  it("connects to the canonical notification websocket endpoint", async () => {
    const { connectNotificationSocket } = await import("./notification.socket")

    connectNotificationSocket()

    expect(MockWebSocket.instances).toHaveLength(1)
    expect(MockWebSocket.instances[0]?.url).toBe("ws://localhost:8080/ws/notifications?token=token-123")
  })

  describe("exponential backoff", () => {
    it("uses exponential backoff for reconnect delays", async () => {
      const { connectNotificationSocket } = await import("./notification.socket")
      const warnSpy = vi.spyOn(console, "warn")

      // First failure
      connectNotificationSocket()
      MockWebSocket.instances[0]?.failBeforeOpen()
      expect(warnSpy).toHaveBeenCalledWith(
        "[notification-socket] Scheduling reconnect with exponential backoff",
        expect.objectContaining({ delayMs: 1000, failureCount: 1 })
      )

      // Advance past first reconnect delay (1s)
      vi.advanceTimersByTime(1100)

      // Second failure
      MockWebSocket.instances[MockWebSocket.instances.length - 1]?.failBeforeOpen()
      expect(warnSpy).toHaveBeenCalledWith(
        "[notification-socket] Scheduling reconnect with exponential backoff",
        expect.objectContaining({ delayMs: 2000, failureCount: 2 })
      )

      // Advance past second reconnect delay (2s)
      vi.advanceTimersByTime(2100)

      // Third failure
      MockWebSocket.instances[MockWebSocket.instances.length - 1]?.failBeforeOpen()
      expect(warnSpy).toHaveBeenCalledWith(
        "[notification-socket] Scheduling reconnect with exponential backoff",
        expect.objectContaining({ delayMs: 4000, failureCount: 3 })
      )
    })
  })

  describe("rapid post-open close detection", () => {
    it("classifies rapid post-open closes as instability failures", async () => {
      const { connectNotificationSocket } = await import("./notification.socket")
      const warnSpy = vi.spyOn(console, "warn")

      connectNotificationSocket()
      // Simulate rapid close after open (within 5-second window)
      MockWebSocket.instances[0]?.rapidPostOpenClose()

      expect(warnSpy).toHaveBeenCalledWith(
        "[notification-socket] Connection failure detected",
        expect.objectContaining({
          failureType: "rapid post-open close",
          totalFailures: 1,
        })
      )

      // Should schedule reconnect instead of suppressing
      vi.advanceTimersByTime(1100)
      expect(MockWebSocket.instances).toHaveLength(2)
    })
  })

  describe("suppression thresholds", () => {
    it("suppresses reconnect after 5 non-recoverable failures", async () => {
      const { connectNotificationSocket } = await import("./notification.socket")
      const errorSpy = vi.spyOn(console, "error")
      const initialCount = MockWebSocket.instances.length

      // Trigger 5 consecutive failures
      for (let i = 0; i < 5; i++) {
        connectNotificationSocket()
        MockWebSocket.instances[MockWebSocket.instances.length - 1]?.failBeforeOpen()

        if (i < 4) {
          const backoffDelay = 1000 * Math.pow(2, i)
          vi.advanceTimersByTime(backoffDelay + 100)
        }
      }

      expect(errorSpy).toHaveBeenCalledWith(
        "[notification-socket] Suppressing reconnect after repeated non-recoverable failures",
        expect.objectContaining({
          totalFailures: 5,
          suppressionTrigger: "threshold exceeded",
        })
      )

      // Further attempts should be suppressed
      const beforeSuppressed = MockWebSocket.instances.length
      connectNotificationSocket()
      // No new socket should be created
      expect(MockWebSocket.instances).toHaveLength(beforeSuppressed)
    })
  })

  describe("reset behavior", () => {
    it("resets failure count after stable connection (5 seconds)", async () => {
      const { connectNotificationSocket } = await import("./notification.socket")
      const logSpy = vi.spyOn(console, "log")

      // First attempt - fail
      connectNotificationSocket()
      MockWebSocket.instances[0]?.failBeforeOpen()
      vi.advanceTimersByTime(1100)

      // Second attempt - fail
      connectNotificationSocket()
      MockWebSocket.instances[MockWebSocket.instances.length - 1]?.failBeforeOpen()
      vi.advanceTimersByTime(2100)

      // Third attempt - succeed and stay stable for 5s
      connectNotificationSocket()
      const successSocket = MockWebSocket.instances[MockWebSocket.instances.length - 1]
      successSocket?.open()

      expect(logSpy).toHaveBeenCalledWith(
        "[notification-socket] Connected successfully",
        expect.any(Object)
      )

      // Advance past the 5-second stability window
      vi.advanceTimersByTime(5100)

      expect(logSpy).toHaveBeenCalledWith(
        "[notification-socket] Connection stable for 5s, reset failure count",
        expect.any(Object)
      )

      // Now fail again - should reset to 1s backoff (not 4s)
      successSocket?.failBeforeOpen()
      expect(console.warn).toHaveBeenCalledWith(
        "[notification-socket] Scheduling reconnect with exponential backoff",
        expect.objectContaining({ delayMs: 1000, failureCount: 1 })
      )
    })

    it("does not reset failure count if connection closes before stability (rapid close)", async () => {
      const { connectNotificationSocket } = await import("./notification.socket")
      const warnSpy = vi.spyOn(console, "warn")

      // First attempt - fail
      connectNotificationSocket()
      MockWebSocket.instances[0]?.failBeforeOpen()
      vi.advanceTimersByTime(1100)

      // Second attempt - rapid close before 5s stability
      connectNotificationSocket()
      MockWebSocket.instances[MockWebSocket.instances.length - 1]?.rapidPostOpenClose()

      // Advance only 1 second (still within 5s window but after rapid close)
      vi.advanceTimersByTime(1100)

      // Should schedule next reconnect with 2s delay (failure count is still 2, not reset to 0)
      expect(warnSpy).toHaveBeenCalledWith(
        "[notification-socket] Scheduling reconnect with exponential backoff",
        expect.objectContaining({ delayMs: 2000, failureCount: 2 })
      )
    })

    it("resets state on manual disconnect", async () => {
      const { connectNotificationSocket, disconnectNotificationSocket } = await import(
        "./notification.socket"
      )
      const logSpy = vi.spyOn(console, "log")

      connectNotificationSocket()
      MockWebSocket.instances[0]?.failBeforeOpen()
      vi.advanceTimersByTime(1100)

      // Manual disconnect
      disconnectNotificationSocket()

      expect(logSpy).toHaveBeenCalledWith(
        "[notification-socket] Disconnected by user",
        expect.any(Object)
      )

      // New token and reconnect should work without suppression
      localStorage.setItem("access_token", "token-456")
      connectNotificationSocket()
      expect(MockWebSocket.instances[MockWebSocket.instances.length - 1]?.url).toContain("token-456")
    })

    it("allows retry again after auth token changes", async () => {
      const { connectNotificationSocket } = await import("./notification.socket")

      // Trigger 5 failures with original token
      for (let i = 0; i < 5; i++) {
        connectNotificationSocket()
        MockWebSocket.instances[MockWebSocket.instances.length - 1]?.failBeforeOpen()
        if (i < 4) {
          vi.advanceTimersByTime(1000 * Math.pow(2, i) + 100)
        }
      }

      // Change token - should clear suppression
      localStorage.setItem("access_token", "token-456")
      connectNotificationSocket()

      // Should succeed with new token
      expect(MockWebSocket.instances[MockWebSocket.instances.length - 1]?.url).toContain("token-456")
    })
  })
})