import { useCallback } from "react"
import { Room, ConnectionState } from "livekit-client"
import { useCallStore } from "../store/call.store"
import {
  initiateCallApi,
  acceptCallApi,
  declineCallApi,
  cancelCallApi,
  endCallApi,
} from "../api/call.service"
import { startRingtone, stopRingtone } from "../utils/ringtone"
import { leaveActiveVoiceRoom } from "./useVoiceRoom"

// Module-level singleton — a call has exactly one LiveKit Room regardless of
// which component (IncomingCallOverlay, ActiveCallBar, ...) is currently
// mounted and calling useCallSession(). A per-instance ref here would mean
// the Room created in one component becomes unreachable the moment a
// different component (e.g. ActiveCallBar, once the call goes active) is the
// one rendering next.
let _callRoom: Room | null = null

function disconnectCallRoom() {
  const room = _callRoom
  if (!room) return
  room.removeAllListeners()
  if (room.state !== ConnectionState.Disconnected) room.disconnect()
  _callRoom = null
}

async function connectCallRoom(liveKitUrl: string, token: string) {
  disconnectCallRoom()
  const room = new Room()
  _callRoom = room
  await room.connect(liveKitUrl, token)
  await room.localParticipant.setMicrophoneEnabled(true)
}

/**
 * The callee connects directly inside acceptCall() below. The caller only
 * learns their outgoing call was accepted via the realtime CALL_ACCEPTED
 * event (see call.socket.ts) — this is the sole place that side ever
 * actually joins the LiveKit room, so without calling this, the caller's
 * audio never connects at all.
 */
export async function connectAsCaller(liveKitUrl: string, token: string) {
  try {
    await connectCallRoom(liveKitUrl, token)
  } catch (err) {
    console.error("[useCallSession] caller connect failed", err)
  }
}

export function useCallSession() {
  const store = useCallStore()

  const initiateCall = useCallback(async (
    targetUserId: string,
    calleeName: string | null,
    calleeAvatarUrl: string | null
  ) => {
    try {
      const res = await initiateCallApi(targetUserId)
      store.setOutgoing({
        callId: res.callId,
        calleeId: targetUserId,
        calleeName,
        calleeAvatarUrl,
      })
      // Ringback for the caller — mirrors the callee's ringtone, which is
      // started from the CALL_INITIATED socket event instead (see call.socket.ts)
      startRingtone()
    } catch (err) {
      console.error("[useCallSession] initiate failed", err)
      throw err
    }
  }, [store])

  const acceptCall = useCallback(async (callId: string) => {
    stopRingtone()
    try {
      // Only one active voice session at a time — answering a call takes
      // priority over an open voice room, the same way a phone call would.
      await leaveActiveVoiceRoom()

      const res = await acceptCallApi(callId)
      const incoming = store.incoming

      store.setIncoming(null)
      store.setActive({
        callId,
        otherUserId: incoming?.callerId ?? "",
        otherUsername: incoming?.callerName ?? null,
        otherAvatarUrl: incoming?.callerAvatarUrl ?? null,
        token: res.token,
        liveKitUrl: res.liveKitUrl,
        startedAt: Date.now(),
      })

      await connectCallRoom(res.liveKitUrl, res.token)
    } catch (err) {
      console.error("[useCallSession] accept failed", err)
      disconnectCallRoom()
      store.setIncoming(null)
      throw err
    }
  }, [store])

  const declineCall = useCallback(async (callId: string) => {
    stopRingtone()
    try {
      await declineCallApi(callId)
    } catch (err) {
      console.error("[useCallSession] decline failed", err)
    } finally {
      store.setIncoming(null)
    }
  }, [store])

  const cancelCall = useCallback(async (callId: string) => {
    stopRingtone()
    try {
      await cancelCallApi(callId)
    } catch (err) {
      console.error("[useCallSession] cancel failed", err)
    } finally {
      store.setOutgoing(null)
    }
  }, [store])

  const endCall = useCallback(async (callId: string) => {
    disconnectCallRoom()
    try {
      await endCallApi(callId)
    } catch (err) {
      console.error("[useCallSession] end failed", err)
    } finally {
      store.setActive(null)
    }
  }, [store])

  const toggleMute = useCallback(async () => {
    const room = _callRoom
    if (!room) return
    const current = room.localParticipant.isMicrophoneEnabled
    await room.localParticipant.setMicrophoneEnabled(!current)
  }, [])

  const isMuted = !(_callRoom?.localParticipant?.isMicrophoneEnabled ?? true)

  return {
    incoming: store.incoming,
    outgoing: store.outgoing,
    active: store.active,
    isMuted,
    initiateCall,
    acceptCall,
    declineCall,
    cancelCall,
    endCall,
    toggleMute,
  }
}
