import { VoiceEventType } from "../constants/voiceEvents"
import { onRealtimeEvent } from "./realtime.socket"
import { useVoiceStore } from "../store/voice.store"

export interface VoiceRoomPayload {
  chatRoomId: string
  userId: string
  username: string
  avatarUrl: string | null
  participantCount: number
  timestamp: number
}

export interface VoiceWsEvent<T = unknown> {
  type: VoiceEventType
  payload: T
  eventId: string
}

const eventHandlers = new Set<(event: VoiceWsEvent) => void>()

export const onVoiceEvent = (handler: (event: VoiceWsEvent) => void) => {
  eventHandlers.add(handler)
  return () => eventHandlers.delete(handler)
}

onRealtimeEvent((msg) => {
  if (!msg.type.startsWith("voice.")) return

  try {
    const event: VoiceWsEvent = {
      type: msg.type as VoiceEventType,
      payload: msg.payload,
      eventId: String(msg.eventId ?? ""),
    }

    handleVoiceEvent(event)
    eventHandlers.forEach((h) => h(event))
  } catch {
    // ignore malformed frames
  }
})

function handleVoiceEvent(event: VoiceWsEvent) {
  const store = useVoiceStore.getState()

  // When the room we are connected to closes, reset connection state.
  if (event.type === VoiceEventType.VOICE_ROOM_CLOSED) {
    const p = event.payload as VoiceRoomPayload
    if (p?.chatRoomId && p.chatRoomId === store.activeVoiceRoomId) {
      store.reset()
    }
  }
}
