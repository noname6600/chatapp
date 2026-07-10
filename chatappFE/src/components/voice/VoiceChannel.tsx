import { Mic, MicOff, Headphones, Volume2, Phone, PhoneOff, Loader2, Monitor, MonitorOff } from "lucide-react"
import { useVoiceRoom } from "../../hooks/useVoiceRoom"
import VoiceParticipantTile from "./VoiceParticipantTile"
import ScreenShareOverlay from "./ScreenShareOverlay"

interface Props {
  chatRoomId: string
}

export default function VoiceChannel({ chatRoomId }: Props) {
  const {
    participants,
    isMuted,
    isDeafened,
    isConnecting,
    isConnected,
    isScreenSharing,
    remoteScreenTracks,
    speakingUserIds,
    join,
    leave,
    toggleMute,
    toggleDeafen,
    toggleScreenShare,
  } = useVoiceRoom(chatRoomId)

  const canScreenShare = typeof navigator !== "undefined" && !!navigator.mediaDevices?.getDisplayMedia

  const myUserId = localStorage.getItem("my_user_id") ?? ""

  if (!isConnected) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 py-6 px-4">
        <p className="text-sm text-gray-500 text-center">
          {isConnecting ? "Connecting to voice..." : "Voice channel — click to join"}
        </p>
        <button
          onClick={join}
          disabled={isConnecting}
          className="flex items-center gap-2 px-4 py-2 bg-green-500 hover:bg-green-600 disabled:opacity-60 text-white text-sm font-medium rounded-lg transition-colors"
        >
          {isConnecting ? (
            <Loader2 size={16} className="animate-spin" />
          ) : (
            <Phone size={16} />
          )}
          {isConnecting ? "Connecting..." : "Join Voice"}
        </button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-3 px-3 py-3">
      {/* Participant grid */}
      <div className="flex flex-wrap gap-3 min-h-[56px]">
        {participants.length === 0 ? (
          <p className="text-xs text-gray-400 italic">No other participants yet</p>
        ) : (
          participants.map((p) => (
            <VoiceParticipantTile
              key={p.userId}
              participant={p}
              isSpeaking={speakingUserIds.has(p.userId)}
              isMuted={p.userId === myUserId ? isMuted : false}
              isMe={p.userId === myUserId}
            />
          ))
        )}
      </div>

      {/* Controls */}
      <div className="flex items-center gap-2 pt-1 border-t border-gray-100">
        <button
          onClick={toggleMute}
          title={isMuted ? "Unmute" : "Mute"}
          className={`p-2 rounded-lg transition-colors ${
            isMuted
              ? "bg-red-100 text-red-600 hover:bg-red-200"
              : "bg-gray-100 text-gray-600 hover:bg-gray-200"
          }`}
        >
          {isMuted ? <MicOff size={16} /> : <Mic size={16} />}
        </button>

        <button
          onClick={toggleDeafen}
          title={isDeafened ? "Undeafen" : "Deafen"}
          className={`p-2 rounded-lg transition-colors ${
            isDeafened
              ? "bg-red-100 text-red-600 hover:bg-red-200"
              : "bg-gray-100 text-gray-600 hover:bg-gray-200"
          }`}
        >
          {isDeafened ? <Volume2 size={16} /> : <Headphones size={16} />}
        </button>

        {canScreenShare && (
          <button
            onClick={toggleScreenShare}
            title={isScreenSharing ? "Stop sharing" : "Share screen"}
            className={`p-2 rounded-lg transition-colors ${
              isScreenSharing
                ? "bg-blue-100 text-blue-600 hover:bg-blue-200"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}
          >
            {isScreenSharing ? <MonitorOff size={16} /> : <Monitor size={16} />}
          </button>
        )}

        <button
          onClick={leave}
          title="Leave voice channel"
          className="ml-auto p-2 rounded-lg bg-red-500 hover:bg-red-600 text-white transition-colors"
        >
          <PhoneOff size={16} />
        </button>
      </div>

      <ScreenShareOverlay tracks={remoteScreenTracks} />
    </div>
  )
}
