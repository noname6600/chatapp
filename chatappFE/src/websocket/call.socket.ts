import { VoiceEventType } from "../constants/voiceEvents"
import { onRealtimeEvent } from "./realtime.socket"
import { useCallStore } from "../store/call.store"

export interface CallEventPayload {
  callId: string
  callerId: string
  callerName: string | null
  callerAvatarUrl: string | null
  calleeId: string
  calleeName: string | null
  calleeAvatarUrl: string | null
  callerLkToken: string | null
  calleeLkToken: string | null
  lkRoomName: string | null
  lkExternalUrl: string | null
  durationSeconds: number | null
  timestamp: number
}

onRealtimeEvent((msg) => {
  if (!msg.type.startsWith("voice.call.")) return

  const payload = msg.payload as CallEventPayload
  if (!payload?.callId) return

  const myUserId = localStorage.getItem("my_user_id")
  if (!myUserId) return

  const store = useCallStore.getState()

  switch (msg.type as VoiceEventType) {
    case VoiceEventType.CALL_INITIATED: {
      if (payload.calleeId !== myUserId) return
      store.setIncoming({
        callId: payload.callId,
        callerId: payload.callerId,
        callerName: payload.callerName,
        callerAvatarUrl: payload.callerAvatarUrl,
      })
      break
    }

    case VoiceEventType.CALL_ACCEPTED: {
      if (payload.callerId !== myUserId) return
      store.setOutgoing(null)
      store.setActive({
        callId: payload.callId,
        otherUserId: payload.calleeId,
        otherUsername: payload.calleeName,
        otherAvatarUrl: payload.calleeAvatarUrl,
        token: payload.callerLkToken ?? "",
        liveKitUrl: payload.lkExternalUrl ?? "",
        startedAt: Date.now(),
      })
      break
    }

    case VoiceEventType.CALL_DECLINED: {
      if (payload.callerId !== myUserId) return
      store.setOutgoing(null)
      break
    }

    case VoiceEventType.CALL_CANCELLED: {
      if (payload.calleeId !== myUserId) return
      store.setIncoming(null)
      break
    }

    case VoiceEventType.CALL_ENDED:
    case VoiceEventType.CALL_MISSED: {
      if (payload.callerId !== myUserId && payload.calleeId !== myUserId) return
      store.setActive(null)
      store.setIncoming(null)
      store.setOutgoing(null)
      break
    }
  }
})
