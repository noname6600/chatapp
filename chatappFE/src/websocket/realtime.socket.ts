import { getWsEndpoint } from "../config/ws.config"
import { requestRealtimeTicket } from "./realtime.ticket"

const WS_URL = getWsEndpoint("REALTIME")
const BACKOFF_BASE = 1000
const BACKOFF_MAX = 30000

let socket: WebSocket | null = null
let isConnecting = false
let manualClose = false
let reconnectTimeout: number | null = null
let reconnectFailureCount = 0
let stabilityTimer: number | null = null

export interface RealtimeMessage {
  type: string
  payload?: unknown
  [key: string]: unknown
}

const eventHandlers = new Set<(msg: RealtimeMessage) => void>()
const openHandlers = new Set<() => void>()
const closeHandlers = new Set<() => void>()

const calculateBackoffDelay = (failureCount: number): number => {
  const exponentialDelay = BACKOFF_BASE * Math.pow(2, Math.max(0, failureCount - 1))
  return Math.min(exponentialDelay, BACKOFF_MAX)
}

export const connectRealtimeSocket = async () => {
  if (isConnecting) return
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return

  const token = localStorage.getItem("access_token")
  if (!token) return

  manualClose = false
  isConnecting = true
  const ticket = await requestRealtimeTicket().catch(() => null)
  isConnecting = false
  if (!ticket) return

  socket = new WebSocket(`${WS_URL}?ticket=${ticket}`)

  socket.onopen = () => {
    if (reconnectTimeout != null) {
      clearTimeout(reconnectTimeout)
      reconnectTimeout = null
    }
    // Only reset backoff after connection has been stable for 5 s — prevents
    // the onopen/onclose rapid-cycle from zeroing the counter every attempt.
    if (stabilityTimer != null) clearTimeout(stabilityTimer)
    stabilityTimer = window.setTimeout(() => {
      stabilityTimer = null
      reconnectFailureCount = 0
    }, 5000)
    openHandlers.forEach((h) => h())
  }

  socket.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data) as RealtimeMessage
      if (!msg?.type) return
      eventHandlers.forEach((h) => h(msg))
    } catch {
      // ignore malformed frames
    }
  }

  socket.onclose = () => {
    socket = null
    if (stabilityTimer != null) {
      clearTimeout(stabilityTimer)
      stabilityTimer = null
    }
    closeHandlers.forEach((h) => h())
    if (!manualClose && localStorage.getItem("access_token")) {
      reconnectFailureCount += 1
      reconnectTimeout = window.setTimeout(
        connectRealtimeSocket,
        calculateBackoffDelay(reconnectFailureCount)
      )
    }
  }

  socket.onerror = () => socket?.close()
}

export const disconnectRealtimeSocket = () => {
  manualClose = true
  isConnecting = false
  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }
  if (stabilityTimer != null) {
    clearTimeout(stabilityTimer)
    stabilityTimer = null
  }
  reconnectFailureCount = 0
  socket?.close()
  socket = null
}

export const sendRealtimeMessage = (msg: unknown): boolean => {
  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(msg))
    return true
  }
  return false
}

export const isRealtimeSocketOpen = (): boolean =>
  socket?.readyState === WebSocket.OPEN

export const onRealtimeEvent = (handler: (msg: RealtimeMessage) => void) => {
  eventHandlers.add(handler)
  return () => eventHandlers.delete(handler)
}

export const onRealtimeOpen = (handler: () => void) => {
  openHandlers.add(handler)
  return () => openHandlers.delete(handler)
}

export const onRealtimeClose = (handler: () => void) => {
  closeHandlers.add(handler)
  return () => closeHandlers.delete(handler)
}
