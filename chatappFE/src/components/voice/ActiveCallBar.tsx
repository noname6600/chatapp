import { useEffect, useState } from "react"
import { Mic, MicOff, PhoneOff } from "lucide-react"
import { useCallSession } from "../../hooks/useCallSession"

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60).toString().padStart(2, "0")
  const s = (seconds % 60).toString().padStart(2, "0")
  return `${m}:${s}`
}

export default function ActiveCallBar() {
  const { active, isMuted, toggleMute, endCall } = useCallSession()
  const [elapsed, setElapsed] = useState(0)

  useEffect(() => {
    if (!active) { setElapsed(0); return }
    const id = setInterval(() => {
      setElapsed(Math.floor((Date.now() - active.startedAt) / 1000))
    }, 1000)
    return () => clearInterval(id)
  }, [active])

  if (!active) return null

  const displayName = active.otherUsername ?? "Unknown"
  const avatar = active.otherAvatarUrl

  return (
    <div className="fixed top-0 left-0 right-0 z-40 flex items-center justify-between px-4 py-2 bg-green-600 text-white shadow-lg">
      <div className="flex items-center gap-3">
        {avatar ? (
          <img src={avatar} alt={displayName} className="w-8 h-8 rounded-full object-cover" />
        ) : (
          <div className="w-8 h-8 rounded-full bg-green-400 flex items-center justify-center text-sm font-bold">
            {displayName[0]?.toUpperCase() ?? "?"}
          </div>
        )}
        <div>
          <p className="text-sm font-semibold leading-none">{displayName}</p>
          <p className="text-xs text-green-200">{formatDuration(elapsed)}</p>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <button
          onClick={toggleMute}
          className={`p-2 rounded-full transition-colors ${isMuted ? "bg-red-500 hover:bg-red-400" : "bg-green-500 hover:bg-green-400"}`}
          title={isMuted ? "Unmute" : "Mute"}
        >
          {isMuted ? <MicOff size={16} /> : <Mic size={16} />}
        </button>
        <button
          onClick={() => endCall(active.callId)}
          className="p-2 rounded-full bg-red-500 hover:bg-red-400 transition-colors"
          title="End call"
        >
          <PhoneOff size={16} />
        </button>
      </div>
    </div>
  )
}
