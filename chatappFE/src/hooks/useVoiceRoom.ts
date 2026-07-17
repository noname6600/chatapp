import { useCallback, useEffect, useRef } from "react"
import {
  Room,
  RoomEvent,
  Track,
  DisconnectReason,
  ScreenSharePresets,
  type RemoteTrack,
  type RemoteAudioTrack,
  type RemoteParticipant,
  type Participant,
  type VideoPreset,
  ConnectionState,
} from "livekit-client"
import { useVoiceStore } from "../store/voice.store"
import { joinVoiceRoomApi, leaveVoiceRoomApi, type VoiceParticipant } from "../api/voice.service"

export type ScreenShareQuality = "auto" | "high" | "medium" | "low"

const SCREEN_SHARE_QUALITY_PRESETS: Record<Exclude<ScreenShareQuality, "auto">, VideoPreset> = {
  high: ScreenSharePresets.h1080fps30,
  medium: ScreenSharePresets.h720fps15,
  low: ScreenSharePresets.h360fps15,
}

// Module-level singletons — enforce one active LK room across all hook instances
let _globalRoom: Room | null = null
let _globalRoomId: string | null = null
// Incrementing this invalidates any in-flight join/retry loop — bumped by both
// a fresh join() call and by leave(), so starting a new attempt or explicitly
// backing out always wins over a stale retry loop still waiting to fire.
let _joinAttemptToken = 0

/**
 * Leaves whatever voice room is currently active, independent of any mounted
 * component/hook instance. Used to enforce "only one active voice session at
 * a time" when starting or accepting a call — see useCallSession.ts.
 */
export async function leaveActiveVoiceRoom() {
  const roomId = useVoiceStore.getState().activeVoiceRoomId
  if (!roomId) return

  const room = _globalRoom
  if (room) {
    room.removeAllListeners()
    if (room.state !== ConnectionState.Disconnected) room.disconnect()
  }
  _globalRoom = null
  _globalRoomId = null

  try {
    await leaveVoiceRoomApi(roomId)
  } catch {
    // best-effort; LiveKit webhook / reconciliation sweep covers this otherwise
  } finally {
    useVoiceStore.getState().reset()
  }
}

export function useVoiceRoom(chatRoomId: string | null) {
  const store = useVoiceStore()

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

  // ── Per-participant volume ──────────────────────────────────────────────────
  // Priority: self-deafen silences everyone regardless of anyone's individual
  // setting; otherwise each participant's own "mute for me" override wins over
  // their slider position, which defaults to full volume if never touched.
  const applyEffectiveVolume = useCallback((identity: string) => {
    const room = _globalRoom
    if (!room) return
    const participant = room.remoteParticipants.get(identity)
    if (!participant) return

    const state = useVoiceStore.getState()
    const effective = state.isDeafened
      ? 0
      : state.remoteMutedForMeByUser[identity]
        ? 0
        : (state.remoteVolumeByUser[identity] ?? 1)

    participant.audioTrackPublications.forEach((pub) => {
      ;(pub.track as RemoteAudioTrack | undefined)?.setVolume(effective)
    })
  }, [])

  // ── Cleanup LiveKit connection ─────────────────────────────────────────────

  const disconnectLiveKit = useCallback(() => {
    const room = _globalRoom
    if (!room) return
    room.removeAllListeners()
    if (room.state !== ConnectionState.Disconnected) {
      room.disconnect()
    }
    _globalRoom = null
    _globalRoomId = null
    detachAllAudio()
  }, [detachAllAudio])

  // ── Join ──────────────────────────────────────────────────────────────────

  const join = useCallback(async (onBackendJoined?: (participants: VoiceParticipant[]) => void) => {
    if (!chatRoomId) return
    // Only a no-op if we're already connected/connecting to THIS room — this
    // used to check the raw global flags, which also silently blocked
    // switching to a *different* room (clicking "Leave & Join" in the switch
    // confirm would call join() for the new room while the old room's
    // isConnected was still true, hitting this guard and returning before
    // ever reaching the "enforce one room at a time" logic below).
    if ((store.isConnecting || store.isConnected) && store.activeVoiceRoomId === chatRoomId) return

    const myToken = ++_joinAttemptToken
    store.setJoinError(null)
    store.setConnecting(true)
    // Set before the first attempt even starts (not just after a successful
    // backend call) so isConnecting/isConnected — scoped to this room via
    // activeVoiceRoomId — read correctly for the whole join+retry duration,
    // including while the very first attempt is still in flight. Also clear
    // isConnected up front: when switching rooms, the old room's stale
    // isConnected=true would otherwise briefly read as "connected" for the
    // *new* room too, for as long as leaving the old room's backend call
    // below takes.
    store.setConnected(false)
    store.setActiveRoom(chatRoomId)

    // Enforce one room at a time — disconnect existing room from any hook instance
    if (_globalRoom && _globalRoomId !== chatRoomId) {
      _globalRoom.removeAllListeners()
      if (_globalRoom.state !== ConnectionState.Disconnected) {
        _globalRoom.disconnect()
      }
      // Clean up any audio elements left by the previous connection
      document.querySelectorAll<HTMLAudioElement>("audio[data-lk-sid]").forEach((el) => el.remove())
      audioElementsRef.current.clear()
      const oldRoomId = _globalRoomId
      _globalRoom = null
      _globalRoomId = null
      if (oldRoomId) {
        try { await leaveVoiceRoomApi(oldRoomId) } catch { /* best-effort */ }
      }
      // reset() wipes isConnecting/activeVoiceRoomId along with everything
      // else — both need restoring for this in-progress join(), otherwise
      // the retry loop below starts with isConnecting already false again.
      store.reset()
      store.setConnecting(true)
      store.setActiveRoom(chatRoomId)
    }

    // Retries indefinitely with capped exponential backoff until it connects,
    // the attempt is superseded by a newer join()/leave() call (token check),
    // or the failure is a mic/device problem retrying can't fix.
    for (let attempt = 1; _joinAttemptToken === myToken; attempt++) {
      try {
        const res = await joinVoiceRoomApi(chatRoomId)

        store.setCredentials(res.token, res.liveKitUrl)
        store.setParticipants(res.participants)
        activeRoomIdRef.current = chatRoomId
        // The backend already recorded us as a participant at this point (well
        // before the LiveKit connection below completes) — let the caller show
        // that immediately instead of waiting for the whole join() to resolve.
        onBackendJoined?.(res.participants)

        // Connect to LiveKit
        const room = new Room()
        _globalRoom = room
        _globalRoomId = chatRoomId

        // ── Room event listeners ──

        room.on(RoomEvent.TrackSubscribed, (track: RemoteTrack, _pub, participant: RemoteParticipant) => {
          if (track.source === Track.Source.ScreenShare) {
            store.setRemoteScreenTrack(participant.identity, track)
            return
          }
          attachAudio(track, participant.sid)
          if (track.kind === Track.Kind.Audio) {
            applyEffectiveVolume(participant.identity)
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

        // Muting doesn't unpublish a track, it just flips its published mute
        // state — LiveKit relays that to everyone automatically, so this needs
        // no backend involvement at all, just listening for it.
        room.on(RoomEvent.TrackMuted, (publication, participant) => {
          if (participant.identity === room.localParticipant.identity) return
          if (publication.source !== Track.Source.Microphone) return
          store.setRemoteMicMuted(participant.identity, true)
        })

        room.on(RoomEvent.TrackUnmuted, (publication, participant) => {
          if (participant.identity === room.localParticipant.identity) return
          if (publication.source !== Track.Source.Microphone) return
          store.setRemoteMicMuted(participant.identity, false)
        })

        // Deafening never touches a published track (it only affects what the
        // deafened user hears locally), so unlike mute there's no native
        // LiveKit signal for it — participant attributes are LiveKit's own
        // mechanism for broadcasting small custom state like this.
        room.on(RoomEvent.ParticipantAttributesChanged, (changedAttributes, participant) => {
          if (participant.identity === room.localParticipant.identity) return
          if (!("deafened" in changedAttributes)) return
          store.setRemoteDeafened(participant.identity, changedAttributes.deafened === "true")
        })

        room.on(RoomEvent.Disconnected, (reason) => {
          if (reason === DisconnectReason.DUPLICATE_IDENTITY) {
            // Same account connected to this room from another browser/device —
            // LiveKit only allows one connection per identity, so that other
            // session is now the live one. It's still legitimately in the room,
            // so this must NOT call leaveVoiceRoomApi (that would kick it too).
            disconnectLiveKit()
            activeRoomIdRef.current = null
            store.reset()
            store.setJoinError("You joined this voice room from another device or tab.")
            return
          }
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

        if (_joinAttemptToken !== myToken) {
          // Cancelled (Leave clicked, or a newer join() started) while this
          // attempt was still connecting — don't leave a connection alive
          // that the user already backed out of.
          disconnectLiveKit()
          leaveVoiceRoomApi(chatRoomId).catch(() => {})
          return
        }

        store.setConnected(true)
        store.setConnecting(false)
        store.setJoinError(null)
        return
      } catch (err) {
        console.error(`[useVoiceRoom] join attempt ${attempt} failed`, err)
        disconnectLiveKit()
        // Best-effort — if the backend had already recorded us as a participant
        // before this failure, tell it we're gone rather than waiting on the
        // LiveKit webhook or the server-side reconciliation sweep.
        leaveVoiceRoomApi(chatRoomId).catch(() => {})

        // setMicrophoneEnabled surfaces mic permission/device errors as a
        // DOMException. Retrying can't fix a permission the user hasn't
        // granted, so this is the one failure mode that stops and requires
        // an explicit new click instead of retrying automatically.
        const isMicError = err instanceof DOMException
          && (err.name === "NotAllowedError" || err.name === "NotFoundError" || err.name === "NotReadableError")
        if (isMicError) {
          store.reset()
          store.setJoinError("Microphone access is required to join voice chat.")
          store.setConnecting(false)
          return
        }

        if (_joinAttemptToken !== myToken) break // cancelled — stop retrying

        store.setJoinError("Having trouble connecting — retrying…")
        const delaySeconds = Math.min(2 ** (attempt - 1), 15)
        await new Promise((resolve) => setTimeout(resolve, delaySeconds * 1000))
      }
    }
    // The loop only ever exits here when the token was invalidated (a newer
    // join() or a leave() cancelled this attempt) — success and terminal
    // mic errors both return from inside the loop above. Clear the
    // transient "connecting" UI rather than leaving it stuck.
    store.setConnecting(false)
  }, [chatRoomId, store, attachAudio, detachAudio, disconnectLiveKit])

  // ── Leave ─────────────────────────────────────────────────────────────────

  const leave = useCallback(async () => {
    // Stop any in-flight join/retry loop first — otherwise a retry already
    // queued behind a setTimeout would fire after this and silently
    // reconnect the room the user just explicitly left.
    _joinAttemptToken++
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
      await _globalRoom?.localParticipant.setMicrophoneEnabled(!next)
    } catch (err) {
      console.error("[useVoiceRoom] toggleMute failed", err)
      store.setMuted(!next) // revert on error
    }
  }, [store])

  const toggleDeafen = useCallback(async () => {
    const next = !store.isDeafened
    store.setDeafened(next)
    try {
      // Broadcast to everyone else — see the ParticipantAttributesChanged
      // listener in join() for the receiving side.
      await _globalRoom?.localParticipant.setAttributes({ deafened: next ? "true" : "false" })
    } catch (err) {
      console.error("[useVoiceRoom] failed to broadcast deafen status", err)
    }
    if (next) {
      // Deafen implies mute
      store.setMuted(true)
      try {
        await _globalRoom?.localParticipant.setMicrophoneEnabled(false)
      } catch { /* ignore */ }
    }
    // Re-apply per-participant effective volume for everyone rather than a
    // blanket set — undeafening must restore each participant's own
    // mute-for-me/slider preference, not blow it away back to full volume.
    _globalRoom?.remoteParticipants.forEach((participant) => {
      applyEffectiveVolume(participant.identity)
    })
  }, [store, applyEffectiveVolume])

  const setParticipantVolume = useCallback((userId: string, volume: number) => {
    store.setParticipantVolumePref(userId, volume)
    applyEffectiveVolume(userId)
  }, [store, applyEffectiveVolume])

  const toggleMuteForMe = useCallback((userId: string) => {
    const next = !store.remoteMutedForMeByUser[userId]
    store.setMutedForMe(userId, next)
    applyEffectiveVolume(userId)
  }, [store, applyEffectiveVolume])

  const toggleScreenShare = useCallback(async (options?: { audio?: boolean; quality?: ScreenShareQuality }) => {
    const room = _globalRoom
    if (!room) return
    const next = !store.isScreenSharing
    store.setScreenSharing(next)
    try {
      if (next) {
        const preset = options?.quality && options.quality !== "auto"
          ? SCREEN_SHARE_QUALITY_PRESETS[options.quality]
          : undefined
        await room.localParticipant.setScreenShareEnabled(
          true,
          { audio: options?.audio, resolution: preset?.resolution },
          { screenShareEncoding: preset?.encoding }
        )
      } else {
        await room.localParticipant.setScreenShareEnabled(false)
      }
    } catch (err) {
      console.error("[useVoiceRoom] toggleScreenShare failed", err)
      store.setScreenSharing(!next)
    }
  }, [store])

  // ── Tab close — disconnect LiveKit so the server gets participant_left webhook
  useEffect(() => {
    const handleUnload = () => {
      _globalRoom?.disconnect()
    }
    window.addEventListener("beforeunload", handleUnload)
    return () => window.removeEventListener("beforeunload", handleUnload)
  }, [])

  return {
    // Scoped to *this* room — the global store only tracks one active voice
    // connection at a time, so without this check, every other room's
    // VoiceChannel would also render as "connected" while you're in one room.
    participants: store.participants,
    isMuted: store.isMuted,
    isDeafened: store.isDeafened,
    isConnecting: store.isConnecting && store.activeVoiceRoomId === chatRoomId,
    isConnected: store.isConnected && store.activeVoiceRoomId === chatRoomId,
    isScreenSharing: store.isScreenSharing,
    screenShareByUser: store.screenShareByUser,
    remoteMicMutedByUser: store.remoteMicMutedByUser,
    remoteDeafenedByUser: store.remoteDeafenedByUser,
    remoteVolumeByUser: store.remoteVolumeByUser,
    remoteMutedForMeByUser: store.remoteMutedForMeByUser,
    speakingUserIds: store.speakingUserIds,
    activeVoiceRoomId: store.activeVoiceRoomId,
    joinError: store.joinError,
    join,
    leave,
    toggleMute,
    toggleDeafen,
    toggleScreenShare,
    setParticipantVolume,
    toggleMuteForMe,
  }
}
