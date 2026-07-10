import { useCallback, useEffect, useRef, useState } from "react"
import {
  Mic, MicOff, Headphones, Volume2, Phone, PhoneOff,
  Loader2, Monitor, MonitorOff, X, MonitorPlay,
} from "lucide-react"
import { useVoiceRoom } from "../../hooks/useVoiceRoom"
import { getVoiceParticipantsApi, type VoiceParticipant } from "../../api/voice.service"
import { onVoiceEvent, type VoiceRoomPayload } from "../../websocket/voice.socket"
import { VoiceEventType } from "../../constants/voiceEvents"
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
  isSelfMuted,
  isScreenSharing,
  onClickScreen,
}: {
  participant: VoiceParticipant
  isSpeaking: boolean
  isSelf: boolean
  isSelfMuted: boolean
  isScreenSharing: boolean
  onClickScreen?: () => void
}) {
  const muted = isSelf ? isSelfMuted : false

  return (
    <div
      className={`flex items-center gap-2.5 px-2 py-1.5 rounded-lg transition-colors ${
        isSpeaking ? "bg-green-50 dark:bg-green-900/20" : "hover:bg-gray-50 dark:hover:bg-gray-800/40"
      }`}
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
        {isSpeaking && (
          <p className="text-[10px] text-green-500 font-medium leading-none mt-0.5">Speaking</p>
        )}
      </div>

      {/* Right icons */}
      <div className="flex items-center gap-1 flex-shrink-0">
        {isScreenSharing && (
          <button
            onClick={onClickScreen}
            title="View screen"
            className="p-1 rounded text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/30 transition-colors"
          >
            <MonitorPlay size={14} />
          </button>
        )}
        {muted ? (
          <MicOff size={13} className="text-red-400" />
        ) : isSpeaking ? (
          <Mic size={13} className="text-green-400" />
        ) : (
          <Mic size={13} className="text-gray-300" />
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
    speakingUserIds,
    join,
    leave,
    toggleMute,
    toggleDeafen,
    toggleScreenShare,
  } = useVoiceRoom(chatRoomId)

  const myUserId = localStorage.getItem("my_user_id") ?? ""
  const canScreenShare =
    typeof navigator !== "undefined" && !!navigator.mediaDevices?.getDisplayMedia

  // ── Local participant list — visible to ALL room viewers ──────────────────
  const [participants, setParticipants] = useState<VoiceParticipant[]>([])

  // Fetch current participants on mount / room change
  useEffect(() => {
    getVoiceParticipantsApi(chatRoomId).then(setParticipants).catch(() => {})
  }, [chatRoomId])

  // Subscribe to join/leave events for real-time updates (for everyone)
  useEffect(() => {
    return onVoiceEvent((event) => {
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
          participants.map((p) => (
            <ParticipantRow
              key={p.userId}
              participant={p}
              isSpeaking={speakingUserIds.has(p.userId)}
              isSelf={p.userId === myUserId}
              isSelfMuted={isMuted}
              isScreenSharing={!!screenShareByUser[p.userId]}
              onClickScreen={() => openScreenShare(p.userId)}
            />
          ))
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
              <button
                onClick={toggleScreenShare}
                title={isScreenSharing ? "Stop sharing" : "Share screen"}
                className={`p-1.5 rounded-lg transition-colors ${
                  isScreenSharing
                    ? "bg-blue-100 text-blue-600 hover:bg-blue-200"
                    : "bg-gray-100 text-gray-600 hover:bg-gray-200"
                }`}
              >
                {isScreenSharing ? <MonitorOff size={14} /> : <Monitor size={14} />}
              </button>
            )}

            <button
              onClick={leave}
              title="Leave voice"
              className="ml-auto p-1.5 rounded-lg bg-red-500 hover:bg-red-600 text-white transition-colors"
            >
              <PhoneOff size={14} />
            </button>
          </div>
        ) : (
          <button
            onClick={join}
            disabled={isConnecting}
            className="mt-1 flex items-center justify-center gap-1.5 w-full py-1.5 rounded-lg bg-green-500 hover:bg-green-600 disabled:opacity-60 text-white text-xs font-medium transition-colors"
          >
            {isConnecting ? (
              <Loader2 size={13} className="animate-spin" />
            ) : (
              <Phone size={13} />
            )}
            {isConnecting ? "Connecting…" : "Join Voice"}
          </button>
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
    </>
  )
}
