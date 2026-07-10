import { useCallback, useEffect, useRef } from "react"
import {
  Room,
  RoomEvent,
  Track,
  type RemoteTrack,
  type RemoteAudioTrack,
  type RemoteParticipant,
  type Participant,
  ConnectionState,
} from "livekit-client"
import { useVoiceStore } from "../store/voice.store"
import { joinVoiceRoomApi, leaveVoiceRoomApi } from "../api/voice.service"

export function useVoiceRoom(chatRoomId: string | null) {
  const store = useVoiceStore()

  // LiveKit Room instance — kept in a ref so it never triggers re-renders
  const roomRef = useRef<Room | null>(null)
  // Map of participantSid → <audio> element for headless audio playback
  const audioElementsRef = useRef<Map<string, HTMLAudioElement>>(new Map())
  // Which chat room we're currently connected to (survives re-renders)
  const activeRoomIdRef = useRef<string | null>(null)

  // ── Audio helpers ──────────────────────────────────────────────────────────

  const attachAudio = useCallback((track: RemoteTrack, sid: string) => {
    if (track.kind !== Track.Kind.Audio) return
    const el = track.attach() as HTMLAudioElement
    el.dataset.lkSid = sid
    audioElementsRef.current.set(sid, el)
    // Headless — not in the React DOM, just needs to exist for the browser to play
    document.body.appendChild(el)
  }, [])

  const detachAudio = useCallback((track: RemoteTrack, sid: string) => {
    track.detach()
    const el = audioElementsRef.current.get(sid)
    if (el) {
      el.remove()
      audioElementsRef.current.delete(sid)
    }
  }, [])

  const detachAllAudio = useCallback(() => {
    audioElementsRef.current.forEach((el) => el.remove())
    audioElementsRef.current.clear()
  }, [])

  // ── Volume helpers for deafen ──────────────────────────────────────────────

  const setRemoteVolume = useCallback((volume: number) => {
    const room = roomRef.current
    if (!room) return
    room.remoteParticipants.forEach((participant: RemoteParticipant) => {
      participant.audioTrackPublications.forEach((pub) => {
        ;(pub.track as RemoteAudioTrack | undefined)?.setVolume(volume)
      })
    })
  }, [])

  // ── Cleanup LiveKit connection ─────────────────────────────────────────────

  const disconnectLiveKit = useCallback(() => {
    const room = roomRef.current
    if (!room) return
    room.removeAllListeners()
    if (room.state !== ConnectionState.Disconnected) {
      room.disconnect()
    }
    roomRef.current = null
    detachAllAudio()
  }, [detachAllAudio])

  // ── Join ──────────────────────────────────────────────────────────────────

  const join = useCallback(async () => {
    if (!chatRoomId) return
    if (store.isConnecting || store.isConnected) return

    store.setConnecting(true)
    try {
      const res = await joinVoiceRoomApi(chatRoomId)

      store.setCredentials(res.token, res.liveKitUrl)
      store.setParticipants(res.participants)
      store.setActiveRoom(chatRoomId)
      activeRoomIdRef.current = chatRoomId

      // Connect to LiveKit
      const room = new Room()
      roomRef.current = room

      // ── Room event listeners ──

      room.on(RoomEvent.TrackSubscribed, (track: RemoteTrack, _pub, participant: RemoteParticipant) => {
        if (track.source === Track.Source.ScreenShare) {
          store.setRemoteScreenTrack(participant.identity, track)
          return
        }
        attachAudio(track, participant.sid)
        if (store.isDeafened && track.kind === Track.Kind.Audio) {
          ;(track as RemoteAudioTrack).setVolume(0)
        }
      })

      room.on(RoomEvent.TrackUnsubscribed, (track: RemoteTrack, _pub, participant: RemoteParticipant) => {
        if (track.source === Track.Source.ScreenShare) {
          store.clearRemoteScreenTrack(participant.identity)
          return
        }
        detachAudio(track, participant.sid)
      })

      room.on(RoomEvent.ActiveSpeakersChanged, (speakers: Participant[]) => {
        store.setSpeaking(speakers.map((s) => s.identity))
      })

      room.on(RoomEvent.Disconnected, () => {
        store.setConnected(false)
        store.setSpeaking([])
      })

      room.on(RoomEvent.Reconnecting, () => {
        store.setConnecting(true)
      })

      room.on(RoomEvent.Reconnected, () => {
        store.setConnecting(false)
        store.setConnected(true)
      })

      await room.connect(res.liveKitUrl, res.token)

      // Enable microphone after connecting
      await room.localParticipant.setMicrophoneEnabled(true)

      store.setConnected(true)
    } catch (err) {
      console.error("[useVoiceRoom] join failed", err)
      disconnectLiveKit()
      store.reset()
    } finally {
      store.setConnecting(false)
    }
  }, [chatRoomId, store, attachAudio, detachAudio, disconnectLiveKit])

  // ── Leave ─────────────────────────────────────────────────────────────────

  const leave = useCallback(async () => {
    const roomId = activeRoomIdRef.current ?? chatRoomId
    if (!roomId) return

    disconnectLiveKit()
    activeRoomIdRef.current = null

    try {
      await leaveVoiceRoomApi(roomId)
    } catch {
      // best-effort; LiveKit webhook handles server-side cleanup if this fails
    } finally {
      store.reset()
    }
  }, [chatRoomId, disconnectLiveKit, store])

  // ── Mute / Deafen ─────────────────────────────────────────────────────────

  const toggleMute = useCallback(async () => {
    const next = !store.isMuted
    store.setMuted(next)
    try {
      await roomRef.current?.localParticipant.setMicrophoneEnabled(!next)
    } catch (err) {
      console.error("[useVoiceRoom] toggleMute failed", err)
      store.setMuted(!next) // revert on error
    }
  }, [store])

  const toggleDeafen = useCallback(async () => {
    const next = !store.isDeafened
    store.setDeafened(next)
    if (next) {
      // Deafen implies mute
      store.setMuted(true)
      try {
        await roomRef.current?.localParticipant.setMicrophoneEnabled(false)
      } catch { /* ignore */ }
      setRemoteVolume(0)
    } else {
      setRemoteVolume(1)
    }
  }, [store, setRemoteVolume])

  const toggleScreenShare = useCallback(async () => {
    const room = roomRef.current
    if (!room) return
    const next = !store.isScreenSharing
    store.setScreenSharing(next)
    try {
      await room.localParticipant.setScreenShareEnabled(next)
    } catch (err) {
      console.error("[useVoiceRoom] toggleScreenShare failed", err)
      store.setScreenSharing(!next)
    }
  }, [store])

  // ── Tab close — disconnect LiveKit so the server gets participant_left webhook
  useEffect(() => {
    const handleUnload = () => {
      roomRef.current?.disconnect()
    }
    window.addEventListener("beforeunload", handleUnload)
    return () => window.removeEventListener("beforeunload", handleUnload)
  }, [])

  // ── Unmount cleanup ────────────────────────────────────────────────────────

  useEffect(() => {
    return () => {
      if (activeRoomIdRef.current) {
        disconnectLiveKit()
        leaveVoiceRoomApi(activeRoomIdRef.current).catch(() => {})
        activeRoomIdRef.current = null
        store.reset()
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return {
    participants: store.participants,
    isMuted: store.isMuted,
    isDeafened: store.isDeafened,
    isConnecting: store.isConnecting,
    isConnected: store.isConnected,
    isScreenSharing: store.isScreenSharing,
    screenShareByUser: store.screenShareByUser,
    speakingUserIds: store.speakingUserIds,
    activeVoiceRoomId: store.activeVoiceRoomId,
    join,
    leave,
    toggleMute,
    toggleDeafen,
    toggleScreenShare,
  }
}
