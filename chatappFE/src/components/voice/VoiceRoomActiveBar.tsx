import { Mic, MicOff, Headphones, Volume2, PhoneOff, Monitor, MonitorOff } from "lucide-react"
import { useVoiceStore } from "../../store/voice.store"
import { useVoiceRoom } from "../../hooks/useVoiceRoom"
import { useRooms } from "../../store/room.store"

export default function VoiceRoomActiveBar() {
  const activeVoiceRoomId = useVoiceStore((s) => s.activeVoiceRoomId)
  const {
    isConnected,
    isMuted,
    isDeafened,
    isScreenSharing,
    leave,
    toggleMute,
    toggleDeafen,
    toggleScreenShare,
  } = useVoiceRoom(activeVoiceRoomId)
  const { roomsById } = useRooms()

  if (!isConnected || !activeVoiceRoomId) return null

  const roomName = roomsById[activeVoiceRoomId]?.name ?? "voice channel"
  const canScreenShare =
    typeof navigator !== "undefined" && !!navigator.mediaDevices?.getDisplayMedia

  return (
    <div className="flex-shrink-0 flex items-center justify-between px-4 py-2 bg-green-600 text-white shadow-lg">
      <div className="flex items-center gap-2 min-w-0">
        <span className="relative flex h-2.5 w-2.5 flex-shrink-0">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-300 opacity-75" />
          <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-green-200" />
        </span>
        <p className="text-sm font-semibold truncate">Voice connected — {roomName}</p>
      </div>
      <div className="flex items-center gap-2 flex-shrink-0">
        <button
          onClick={toggleMute}
          className={`p-2 rounded-full transition-colors ${isMuted ? "bg-red-500 hover:bg-red-400" : "bg-green-500 hover:bg-green-400"}`}
          title={isMuted ? "Unmute" : "Mute"}
        >
          {isMuted ? <MicOff size={16} /> : <Mic size={16} />}
        </button>
        <button
          onClick={toggleDeafen}
          className={`p-2 rounded-full transition-colors ${isDeafened ? "bg-red-500 hover:bg-red-400" : "bg-green-500 hover:bg-green-400"}`}
          title={isDeafened ? "Undeafen" : "Deafen"}
        >
          {isDeafened ? <Volume2 size={16} /> : <Headphones size={16} />}
        </button>
        {canScreenShare && (
          <button
            onClick={toggleScreenShare}
            className={`p-2 rounded-full transition-colors ${isScreenSharing ? "bg-blue-500 hover:bg-blue-400" : "bg-green-500 hover:bg-green-400"}`}
            title={isScreenSharing ? "Stop sharing" : "Share screen"}
          >
            {isScreenSharing ? <MonitorOff size={16} /> : <Monitor size={16} />}
          </button>
        )}
        <button
          onClick={() => leave()}
          className="p-2 rounded-full bg-red-500 hover:bg-red-400 transition-colors"
          title="Leave voice"
        >
          <PhoneOff size={16} />
        </button>
      </div>
    </div>
  )
}
