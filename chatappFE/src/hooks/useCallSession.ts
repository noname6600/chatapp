import { useCallback, useRef } from "react"
import { Room, ConnectionState } from "livekit-client"
import { useCallStore } from "../store/call.store"
import {
  initiateCallApi,
  acceptCallApi,
  declineCallApi,
  cancelCallApi,
  endCallApi,
} from "../api/call.service"

export function useCallSession() {
  const store = useCallStore()
  const roomRef = useRef<Room | null>(null)

  const disconnectRoom = useCallback(() => {
    const room = roomRef.current
    if (!room) return
    room.removeAllListeners()
    if (room.state !== ConnectionState.Disconnected) room.disconnect()
    roomRef.current = null
  }, [])

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
    } catch (err) {
      console.error("[useCallSession] initiate failed", err)
      throw err
    }
  }, [store])

  const acceptCall = useCallback(async (callId: string) => {
    try {
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

      // Connect callee to LiveKit room
      const room = new Room()
      roomRef.current = room
      await room.connect(res.liveKitUrl, res.token)
      await room.localParticipant.setMicrophoneEnabled(true)
    } catch (err) {
      console.error("[useCallSession] accept failed", err)
      disconnectRoom()
      store.setIncoming(null)
      throw err
    }
  }, [store, disconnectRoom])

  const declineCall = useCallback(async (callId: string) => {
    try {
      await declineCallApi(callId)
    } catch (err) {
      console.error("[useCallSession] decline failed", err)
    } finally {
      store.setIncoming(null)
    }
  }, [store])

  const cancelCall = useCallback(async (callId: string) => {
    try {
      await cancelCallApi(callId)
    } catch (err) {
      console.error("[useCallSession] cancel failed", err)
    } finally {
      store.setOutgoing(null)
    }
  }, [store])

  const endCall = useCallback(async (callId: string) => {
    disconnectRoom()
    try {
      await endCallApi(callId)
    } catch (err) {
      console.error("[useCallSession] end failed", err)
    } finally {
      store.setActive(null)
    }
  }, [store, disconnectRoom])

  const toggleMute = useCallback(async () => {
    const room = roomRef.current
    if (!room) return
    const current = room.localParticipant.isMicrophoneEnabled
    await room.localParticipant.setMicrophoneEnabled(!current)
  }, [])

  const isMuted = !(roomRef.current?.localParticipant?.isMicrophoneEnabled ?? true)

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
