import { getWsEndpoint } from "../config/ws.config"

let socket: WebSocket | null = null
let reconnectTimeout: number | null = null
let manualClose = false
let reconnectFailureCount = 0
let suppressedConnectionSignature: string | null = null
let connectionStartTime: number | null = null
let stabilityTimeout: number | null = null

const eventHandlers = new Set<(event: NotificationWsEvent) => void>()
const openHandlers = new Set<() => void>()

const WS_URL = getWsEndpoint("NOTIFICATION")
const BACKOFF_BASE = 1000 // 1 second
const BACKOFF_MAX = 30000 // 30 seconds
const RAPID_CLOSE_WINDOW = 5000 // 5 seconds
const NON_RECOVERABLE_FAILURE_THRESHOLD = 5

export interface NotificationWsEvent<T = unknown> {
  type: string
  data: T
}

const getConnectionSignature = (token: string) => `${WS_URL}|${token}`

/**
 * Calculate capped exponential backoff delay: min(base * 2^failures, max_delay)
 */
const calculateBackoffDelay = (failureCount: number): number => {
  const exponentialDelay = BACKOFF_BASE * Math.pow(2, Math.max(0, failureCount - 1))
  return Math.min(exponentialDelay, BACKOFF_MAX)
}

/**
 * Check if a close event occurred rapidly after open (within stability window)
 */
const isRapidPostOpenClose = (): boolean => {
  if (!connectionStartTime) return false
  const elapsed = Date.now() - connectionStartTime
  return elapsed < RAPID_CLOSE_WINDOW
}

export const connectNotificationSocket = () => {
  if (
    socket &&
    (socket.readyState === WebSocket.OPEN ||
      socket.readyState === WebSocket.CONNECTING)
  ) {
    return
  }

  const token = localStorage.getItem("access_token")
  if (!token) return

  const connectionSignature = getConnectionSignature(token)
  if (suppressedConnectionSignature === connectionSignature) {
    console.warn("[notification-socket] Reconnect suppressed for current session", {
      endpoint: WS_URL,
      suppressionReason: "repeated non-recoverable failures",
    })
    return
  }

  if (reconnectTimeout != null) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }

  manualClose = false
  let didOpen = false
  console.log("[notification-socket] Attempting connection", {
    endpoint: WS_URL,
    failureCount: reconnectFailureCount,
  })
  socket = new WebSocket(`${WS_URL}?token=${token}`)
  connectionStartTime = Date.now()

  socket.onopen = () => {
    didOpen = true
    suppressedConnectionSignature = null
    // Don't reset connectionStartTime yet - we need it to detect rapid post-open closes
    console.log("[notification-socket] Connected successfully", {
      endpoint: WS_URL,
      totalAttempts: reconnectFailureCount + 1,
    })

    // Reset failure count only after connection is stable for 5 seconds
    // This prevents rapid-close loops from bypassing failure accumulation
    if (stabilityTimeout != null) {
      clearTimeout(stabilityTimeout)
    }
    stabilityTimeout = window.setTimeout(() => {
      reconnectFailureCount = 0
      stabilityTimeout = null
      console.log("[notification-socket] Connection stable for 5s, reset failure count", {
        endpoint: WS_URL,
      })
    }, RAPID_CLOSE_WINDOW)

    openHandlers.forEach((handler) => handler())
  }

  socket.onmessage = (event) => {
    try {
      const data: NotificationWsEvent = JSON.parse(event.data)
      console.log("[notification-socket] Message received", {
        type: data.type,
        rawPayload: data.data,
        handlerCount: eventHandlers.size,
      })
      eventHandlers.forEach((handler) => handler(data))
    } catch {
      console.warn("[notification-socket] Failed to parse message — raw:", event.data)
    }
  }

  socket.onclose = (event) => {
    socket = null
    
    // Cancel stability reset timer if connection closes before becoming stable
    if (stabilityTimeout != null) {
      clearTimeout(stabilityTimeout)
      stabilityTimeout = null
    }
    
    const rapidClose = isRapidPostOpenClose()

    console.warn("[notification-socket] Connection closed", {
      code: event.code,
      reason: event.reason || "(no reason)",
      wasClean: event.wasClean,
      didOpen,
      rapidClose,
      currentFailureCount: reconnectFailureCount,
    })

    // Classify instability: pre-open closes AND rapid post-open closes (both within stability window)
    if (!manualClose && (!didOpen || rapidClose)) {
      reconnectFailureCount += 1
      const reason = didOpen ? "rapid post-open close" : "pre-open close"
      console.warn("[notification-socket] Connection failure detected", {
        code: event.code,
        reason: event.reason,
        failureType: reason,
        totalFailures: reconnectFailureCount,
        endpoint: WS_URL,
      })

      if (reconnectFailureCount >= NON_RECOVERABLE_FAILURE_THRESHOLD) {
        suppressedConnectionSignature = connectionSignature
        console.error("[notification-socket] Suppressing reconnect after repeated non-recoverable failures", {
          endpoint: WS_URL,
          totalFailures: reconnectFailureCount,
          suppressionTrigger: "threshold exceeded",
        })
        connectionStartTime = null
        return
      }
    }

    if (!manualClose && localStorage.getItem("access_token")) {
      const backoffDelay = calculateBackoffDelay(reconnectFailureCount)
      console.warn("[notification-socket] Scheduling reconnect with exponential backoff", {
        endpoint: WS_URL,
        delayMs: backoffDelay,
        failureCount: reconnectFailureCount,
        maxBackoffMs: BACKOFF_MAX,
      })
      reconnectTimeout = window.setTimeout(connectNotificationSocket, backoffDelay)
    }
    
    connectionStartTime = null
  }

  socket.onerror = () => {
    console.error("[notification-socket] WebSocket error", {
      endpoint: WS_URL,
      readyState: socket?.readyState,
    })
    socket?.close()
  }
}

export const disconnectNotificationSocket = () => {
  manualClose = true

  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }

  if (stabilityTimeout) {
    clearTimeout(stabilityTimeout)
    stabilityTimeout = null
  }

  socket?.close()
  socket = null
  
  // Reset state on manual disconnect
  reconnectFailureCount = 0
  suppressedConnectionSignature = null
  connectionStartTime = null
  console.log("[notification-socket] Disconnected by user", { endpoint: WS_URL })
}

export const onNotificationEvent = (handler: (event: NotificationWsEvent) => void) => {
  eventHandlers.add(handler)

  return () => {
    eventHandlers.delete(handler)
  }
}

export const onNotificationSocketOpen = (handler: () => void) => {
  openHandlers.add(handler)

  return () => {
    openHandlers.delete(handler)
  }
}