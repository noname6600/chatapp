import { onRealtimeEvent, onRealtimeOpen } from "./realtime.socket"

export interface NotificationWsEvent<T = unknown> {
  type: string
  data: T
}

const eventHandlers = new Set<(event: NotificationWsEvent) => void>()
const openHandlers = new Set<() => void>()

// Route incoming notification messages from the unified socket
onRealtimeEvent((msg) => {
  if (!msg.type.startsWith("notification.")) return
  const event: NotificationWsEvent = {
    type: msg.type,
    data: msg.payload,
  }
  eventHandlers.forEach((handler) => handler(event))
})

onRealtimeOpen(() => {
  openHandlers.forEach((handler) => handler())
})

export const onNotificationEvent = (handler: (event: NotificationWsEvent) => void) => {
  eventHandlers.add(handler)
  return () => eventHandlers.delete(handler)
}

export const onNotificationSocketOpen = (handler: () => void) => {
  openHandlers.add(handler)
  return () => openHandlers.delete(handler)
}
