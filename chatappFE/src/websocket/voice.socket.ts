import { VoiceEventType } from "../constants/voiceEvents"
import { onRealtimeEvent } from "./realtime.socket"
import { useVoiceStore } from "../store/voice.store"
import type { VoiceParticipant } from "../api/voice.service"

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

  switch (event.type) {
    case VoiceEventType.VOICE_ROOM_JOINED: {
      const p = event.payload as VoiceRoomPayload
      if (!p?.userId || !p?.chatRoomId) return
      if (p.chatRoomId !== store.activeVoiceRoomId) return
      const participant: VoiceParticipant = {
        userId: p.userId,
        username: p.username,
        avatarUrl: p.avatarUrl,
        joinedAt: p.timestamp,
      }
      store.addParticipant(participant)
      break
    }

    case VoiceEventType.VOICE_ROOM_LEFT: {
      const p = event.payload as VoiceRoomPayload
      if (!p?.userId || !p?.chatRoomId) return
      if (p.chatRoomId !== store.activeVoiceRoomId) return
      store.removeParticipant(p.userId)
      break
    }

    case VoiceEventType.VOICE_ROOM_CLOSED: {
      const p = event.payload as VoiceRoomPayload
      if (!p?.chatRoomId) return
      if (p.chatRoomId !== store.activeVoiceRoomId) return
      store.reset()
      break
    }
  }
}
