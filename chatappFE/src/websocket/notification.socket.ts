let socket: WebSocket | null = null
let reconnectTimeout: number | null = null
let manualClose = false

const eventHandlers = new Set<(event: NotificationWsEvent) => void>()
const openHandlers = new Set<() => void>()

const WS_URL = "ws://localhost:8086/ws/notifications"
const RECONNECT_DELAY = 3000

export interface NotificationWsEvent<T = unknown> {
  type: string
  data: T
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

  if (reconnectTimeout != null) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }

  manualClose = false
  socket = new WebSocket(`${WS_URL}?token=${token}`)

  socket.onopen = () => {
    openHandlers.forEach((handler) => handler())
  }

  socket.onmessage = (event) => {
    try {
      const data: NotificationWsEvent = JSON.parse(event.data)
      eventHandlers.forEach((handler) => handler(data))
    } catch {
      // ignore malformed events
    }
  }

  socket.onclose = () => {
    socket = null

    if (!manualClose && localStorage.getItem("access_token")) {
      reconnectTimeout = window.setTimeout(connectNotificationSocket, RECONNECT_DELAY)
    }
  }

  socket.onerror = () => {
    socket?.close()
  }
}

export const disconnectNotificationSocket = () => {
  manualClose = true

  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }

  socket?.close()
  socket = null
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