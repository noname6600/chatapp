import { useCallback, useEffect, useRef, useState } from "react"
import {
  Mic, MicOff, Headphones, Volume2, Phone, PhoneOff,
  Loader2, X, MonitorPlay, AlertTriangle,
} from "lucide-react"
import { useVoiceRoom } from "../../hooks/useVoiceRoom"
import { useCallSession } from "../../hooks/useCallSession"
import { getVoiceParticipantsApi, type VoiceParticipant } from "../../api/voice.service"
import { onVoiceEvent, type VoiceRoomPayload } from "../../websocket/voice.socket"
import { VoiceEventType } from "../../constants/voiceEvents"
import { Button } from "../ui/Button"
import ParticipantSettingsMenu from "./ParticipantSettingsMenu"
import ScreenShareControl from "./ScreenShareControl"
import type { RemoteTrack } from "livekit-client"

interface Props {
  chatRoomId: string
}

function Avatar({ src, name, size = 40 }: { src: string | null; name: string; size?: number }) {
  const initials = (name || "?")[0].toUpperCase()
  return src ? (
    <img
      src={src}
      alt={name}
      style={{ width: size, height: size }}
      className="rounded-full object-cover flex-shrink-0"
    />
  ) : (
    <div
      style={{ width: size, height: size }}
      className="rounded-full bg-indigo-500 flex items-center justify-center text-white font-semibold flex-shrink-0 text-sm"
    >
      {initials}
    </div>
  )
}

function ScreenModal({
  track,
  username,
  onClose,
}: {
  track: RemoteTrack
  username: string
  onClose: () => void
}) {
  const videoRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    const el = videoRef.current
    if (!el) return
    track.attach(el)
    return () => { track.detach(el) }
  }, [track])

  return (
    <div className="fixed inset-0 z-50 bg-black/80 flex flex-col items-center justify-center p-4" onClick={onClose}>
      <div className="w-full max-w-5xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-2 text-white">
          <span className="font-semibold">{username}'s screen</span>
          <button onClick={onClose} className="p-1 hover:bg-white/10 rounded">
            <X size={20} />
          </button>
        </div>
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className="w-full rounded-lg shadow-2xl object-contain bg-black max-h-[75vh]"
        />
      </div>
    </div>
  )
}

function ParticipantRow({
  participant,
  isSpeaking,
  isSelf,
  isMuted,
  isDeafened,
  isScreenSharing,
  isConnectingSelf,
  onClickScreen,
  onToggleMute,
  onToggleDeafen,
  mutedForMe,
  volume,
  onToggleMuteForMe,
  onVolumeChange,
}: {
  participant: VoiceParticipant
  isSpeaking: boolean
  isSelf: boolean
  /** Self: the shared mute/deafen state. Other: derived from LiveKit's native track-mute signal / participant attributes. */
  isMuted: boolean
  isDeafened: boolean
  isScreenSharing: boolean
  isConnectingSelf?: boolean
  onClickScreen?: () => void
  onToggleMute: () => void
  onToggleDeafen: () => void
  /** Other-participant-only — local-only "mute for me" + per-user volume preference */
  mutedForMe: boolean
  volume: number
  onToggleMuteForMe: () => void
  onVolumeChange: (volume: number) => void
}) {
  return (
    <div
      className={`flex items-center gap-2.5 px-2 py-1.5 rounded-lg transition-all duration-300 ${
        isSpeaking ? "bg-green-50 dark:bg-green-900/20" : "hover:bg-gray-50 dark:hover:bg-gray-800/40"
      } ${isConnectingSelf ? "opacity-50 blur-[1px]" : ""}`}
    >
      {/* Avatar with speaking ring */}
      <div
        className={`rounded-full flex-shrink-0 transition-all duration-150 ${
          isSpeaking ? "ring-2 ring-green-400 ring-offset-1" : ""
        }`}
      >
        <Avatar src={participant.avatarUrl} name={participant.username || "?"} size={34} />
      </div>

      {/* Name + badge */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">
          {participant.username || "Unknown"}
          {isSelf && <span className="ml-1 text-xs text-gray-400 font-normal">(you)</span>}
        </p>
        {isConnectingSelf ? (
          <p className="text-[10px] text-gray-400 font-medium leading-none mt-0.5">Connecting…</p>
        ) : isSpeaking ? (
          <p className="text-[10px] text-green-500 font-medium leading-none mt-0.5">Speaking</p>
        ) : null}
      </div>

      {/* Right icons — mute/deafen status badges, settings, watch-screen */}
      <div className="flex items-center gap-1 flex-shrink-0">
        {isMuted ? (
          <span title="Muted"><MicOff size={13} className="text-red-400" /></span>
        ) : isSpeaking ? (
          <Mic size={13} className="text-green-400" />
        ) : (
          <Mic size={13} className="text-gray-300" />
        )}
        {isDeafened ? (
          <span title="Deafened"><Headphones size={13} className="text-red-400" /></span>
        ) : (
          <Headphones size={13} className="text-gray-200 dark:text-gray-600" />
        )}
        <ParticipantSettingsMenu
          participant={participant}
          isSelf={isSelf}
          isMuted={isMuted}
          isDeafened={isDeafened}
          onToggleMute={onToggleMute}
          onToggleDeafen={onToggleDeafen}
          mutedForMe={mutedForMe}
          volume={volume}
          onToggleMuteForMe={onToggleMuteForMe}
          onVolumeChange={onVolumeChange}
        />
        {isScreenSharing && (
          <button
            onClick={onClickScreen}
            title="View screen"
            className="p-1 rounded text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/30 transition-colors"
          >
            <MonitorPlay size={14} />
          </button>
        )}
      </div>
    </div>
  )
}

export default function VoiceChannel({ chatRoomId }: Props) {
  const {
    isMuted,
    isDeafened,
    isConnecting,
    isConnected,
    isScreenSharing,
    screenShareByUser,
    remoteMicMutedByUser,
    remoteDeafenedByUser,
    remoteVolumeByUser,
    remoteMutedForMeByUser,
    speakingUserIds,
    activeVoiceRoomId,
    joinError,
    join,
    leave,
    toggleMute,
    toggleDeafen,
    toggleScreenShare,
    setParticipantVolume,
    toggleMuteForMe,
  } = useVoiceRoom(chatRoomId)

  const { active: activeCall, outgoing: outgoingCall, incoming: incomingCall, endCall, cancelCall, declineCall } = useCallSession()

  const [leavingStale, setLeavingStale] = useState(false)
  // "room" = already connected to a different voice room; "call" = on a call
  const [switchConfirmReason, setSwitchConfirmReason] = useState<"room" | "call" | null>(null)

  const myUserId = localStorage.getItem("my_user_id") ?? ""
  const canScreenShare =
    typeof navigator !== "undefined" && !!navigator.mediaDevices?.getDisplayMedia

  // ── Local participant list — visible to ALL room viewers ──────────────────
  const [participants, setParticipants] = useState<VoiceParticipant[]>([])

  const refreshParticipants = useCallback(() => {
    getVoiceParticipantsApi(chatRoomId).then(setParticipants).catch(() => {})
  }, [chatRoomId])

  // Fetch current participants on mount / room change
  useEffect(() => {
    refreshParticipants()
  }, [refreshParticipants])

  // Re-fetch immediately after our own join/leave completes, instead of relying
  // solely on the realtime echo — that echo can race with this component's own
  // room-change effects (e.g. switching rooms right after joining), silently
  // dropping the update and leaving our own name/avatar missing or stale.
  const handleJoinClick = useCallback(() => {
    if (activeCall || outgoingCall || incomingCall) {
      setSwitchConfirmReason("call")
    } else if (activeVoiceRoomId && activeVoiceRoomId !== chatRoomId) {
      setSwitchConfirmReason("room")
    } else {
      join(setParticipants).then(refreshParticipants)
    }
  }, [activeCall, outgoingCall, incomingCall, activeVoiceRoomId, chatRoomId, join, refreshParticipants])

  const confirmSwitch = useCallback(async () => {
    setSwitchConfirmReason(null)
    if (activeCall) await endCall(activeCall.callId)
    else if (outgoingCall) await cancelCall(outgoingCall.callId)
    else if (incomingCall) await declineCall(incomingCall.callId)
    join(setParticipants).then(refreshParticipants)
  }, [activeCall, outgoingCall, incomingCall, endCall, cancelCall, declineCall, join, refreshParticipants])

  // After a page refresh the LiveKit connection is gone but the server may still
  // list us as a participant (webhook cleanup hasn't caught up yet). In that
  // state isConnected is false so the normal controls are hidden — surface an
  // explicit way to force-leave the stale session instead of being stuck.
  const isSelfStale =
    !isConnected && !isConnecting && myUserId !== "" && participants.some((p) => p.userId === myUserId)

  // Subscribe to join/leave events for real-time updates (for everyone)
  useEffect(() => {
    const off = onVoiceEvent((event) => {
      const p = event.payload as VoiceRoomPayload
      if (!p?.chatRoomId || p.chatRoomId !== chatRoomId) return

      if (event.type === VoiceEventType.VOICE_ROOM_JOINED) {
        if (!p.userId) return
        setParticipants((prev) =>
          prev.some((x) => x.userId === p.userId)
            ? prev
            : [
                ...prev,
                {
                  userId: p.userId,
                  username: p.username ?? "Unknown",
                  avatarUrl: p.avatarUrl,
                  joinedAt: p.timestamp,
                },
              ]
        )
      } else if (event.type === VoiceEventType.VOICE_ROOM_LEFT) {
        setParticipants((prev) => prev.filter((x) => x.userId !== p.userId))
      } else if (event.type === VoiceEventType.VOICE_ROOM_CLOSED) {
        setParticipants([])
      }
    })
    return () => { off() }
  }, [chatRoomId])

  // ── Screen share viewer ───────────────────────────────────────────────────
  const [viewingScreen, setViewingScreen] = useState<{
    userId: string
    track: RemoteTrack
  } | null>(null)

  const openScreenShare = useCallback(
    (userId: string) => {
      const track = screenShareByUser[userId]
      if (track) setViewingScreen({ userId, track })
    },
    [screenShareByUser]
  )

  // Close modal if the track disappears
  useEffect(() => {
    if (viewingScreen && !screenShareByUser[viewingScreen.userId]) {
      setViewingScreen(null)
    }
  }, [screenShareByUser, viewingScreen])

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <>
      <div className="flex flex-col gap-1 px-2 py-2">
        {/* Participant rows — always visible to everyone in the chat room */}
        {participants.length === 0 ? (
          <p className="text-xs text-gray-400 italic px-2 py-1">No one in voice</p>
        ) : (
          participants.map((p) => {
            const isSelfRow = p.userId === myUserId
            return (
              <ParticipantRow
                key={p.userId}
                participant={p}
                isSpeaking={speakingUserIds.has(p.userId)}
                isSelf={isSelfRow}
                isMuted={isSelfRow ? isMuted : !!remoteMicMutedByUser[p.userId]}
                isDeafened={isSelfRow ? isDeafened : !!remoteDeafenedByUser[p.userId]}
                isScreenSharing={!!screenShareByUser[p.userId]}
                isConnectingSelf={isSelfRow && isConnecting}
                onClickScreen={() => openScreenShare(p.userId)}
                onToggleMute={toggleMute}
                onToggleDeafen={toggleDeafen}
                mutedForMe={!!remoteMutedForMeByUser[p.userId]}
                volume={remoteVolumeByUser[p.userId] ?? 1}
                onToggleMuteForMe={() => toggleMuteForMe(p.userId)}
                onVolumeChange={(volume) => setParticipantVolume(p.userId, volume)}
              />
            )
          })
        )}

        {/* Controls — only shown when connected */}
        {isConnected ? (
          <div className="flex items-center gap-1.5 pt-2 mt-1 border-t border-gray-100 dark:border-gray-700">
            <button
              onClick={toggleMute}
              title={isMuted ? "Unmute" : "Mute"}
              className={`p-1.5 rounded-lg transition-colors text-xs flex items-center gap-1 ${
                isMuted
                  ? "bg-red-100 text-red-600 hover:bg-red-200"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              {isMuted ? <MicOff size={14} /> : <Mic size={14} />}
            </button>

            <button
              onClick={toggleDeafen}
              title={isDeafened ? "Undeafen" : "Deafen"}
              className={`p-1.5 rounded-lg transition-colors ${
                isDeafened
                  ? "bg-red-100 text-red-600 hover:bg-red-200"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              {isDeafened ? <Volume2 size={14} /> : <Headphones size={14} />}
            </button>

            {canScreenShare && (
              <ScreenShareControl
                isScreenSharing={isScreenSharing}
                toggleScreenShare={toggleScreenShare}
                buttonClassName={`p-1.5 rounded-lg transition-colors ${
                  isScreenSharing
                    ? "bg-blue-100 text-blue-600 hover:bg-blue-200"
                    : "bg-gray-100 text-gray-600 hover:bg-gray-200"
                }`}
                iconSize={14}
              />
            )}

            <button
              onClick={() => leave().then(refreshParticipants)}
              title="Leave voice"
              className="ml-auto p-1.5 rounded-lg bg-red-500 hover:bg-red-600 text-white transition-colors"
            >
              <PhoneOff size={14} />
            </button>
          </div>
        ) : isSelfStale ? (
          <div className="flex items-center gap-2 pt-2 mt-1 border-t border-gray-100 dark:border-gray-700">
            <p className="text-xs text-gray-400 flex-1">
              {joinError ?? "Still shown as in this room"}
            </p>
            <button
              onClick={handleJoinClick}
              disabled={isConnecting}
              title="Reconnect"
              className="flex items-center gap-1 px-2 py-1.5 rounded-lg bg-amber-500 hover:bg-amber-600 disabled:opacity-60 text-white text-xs font-medium transition-colors"
            >
              {isConnecting ? <Loader2 size={13} className="animate-spin" /> : <Phone size={13} />}
              Reconnect
            </button>
            <button
              onClick={async () => {
                setLeavingStale(true)
                try {
                  await leave()
                  refreshParticipants()
                } finally {
                  setLeavingStale(false)
                }
              }}
              disabled={leavingStale}
              title="Leave voice"
              className="flex items-center gap-1 px-2 py-1.5 rounded-lg bg-red-500 hover:bg-red-600 disabled:opacity-60 text-white text-xs font-medium transition-colors"
            >
              {leavingStale ? <Loader2 size={13} className="animate-spin" /> : <PhoneOff size={13} />}
              Leave
            </button>
          </div>
        ) : (
          <div className="mt-1 flex items-center gap-1.5">
            <button
              onClick={handleJoinClick}
              disabled={isConnecting}
              className="flex-1 flex items-center justify-center gap-1.5 py-1.5 rounded-lg bg-green-500 hover:bg-green-600 disabled:opacity-60 text-white text-xs font-medium transition-colors"
            >
              {isConnecting ? (
                <Loader2 size={13} className="animate-spin" />
              ) : (
                <Phone size={13} />
              )}
              {isConnecting ? "Connecting…" : "Join Voice"}
            </button>
            {/* Always give a way out, even mid-connect — no dead end while
                waiting on a slow or hanging connection attempt. */}
            {isConnecting && (
              <button
                onClick={() => leave().then(refreshParticipants)}
                title="Cancel"
                className="p-1.5 rounded-lg bg-red-500 hover:bg-red-600 text-white transition-colors"
              >
                <PhoneOff size={14} />
              </button>
            )}
          </div>
        )}
        {joinError && !isSelfStale && (
          <p className="mt-1 text-[11px] text-red-500 text-center">{joinError}</p>
        )}
      </div>

      {/* Screen share modal */}
      {viewingScreen && (
        <ScreenModal
          track={viewingScreen.track}
          username={
            participants.find((p) => p.userId === viewingScreen.userId)?.username ?? "Unknown"
          }
          onClose={() => setViewingScreen(null)}
        />
      )}

      {/* Confirm switching away from another active voice session */}
      {switchConfirmReason && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full space-y-4">
            <div className="flex items-center gap-3">
              <AlertTriangle size={24} className="text-amber-500" />
              <h2 className="text-lg font-semibold text-gray-900">
                {switchConfirmReason === "call" ? "Leave call to join voice?" : "Switch voice rooms?"}
              </h2>
            </div>

            <p className="text-sm text-gray-600">
              {switchConfirmReason === "call"
                ? "You have an active call. Joining this voice room will end it."
                : "You're already in another voice room. Joining this one will leave that room."}
              {" "}Are you sure you want to continue?
            </p>

            <div className="flex gap-3 justify-end">
              <Button variant="outline" size="sm" onClick={() => setSwitchConfirmReason(null)}>
                Cancel
              </Button>
              <Button variant="destructive" size="sm" onClick={confirmSwitch}>
                {switchConfirmReason === "call" ? "End Call & Join" : "Leave & Join"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
