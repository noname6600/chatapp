import { useEffect, useRef, useState } from "react"
import { User, Mic, MicOff, Headphones, VolumeX, Volume2, Settings } from "lucide-react"
import { useUserOverlay } from "../../store/userOverlay.store"
import type { VoiceParticipant } from "../../api/voice.service"

interface Props {
  participant: VoiceParticipant
  isSelf: boolean
  // Self-only — reflects the existing global mute/deafen state
  isMuted: boolean
  isDeafened: boolean
  onToggleMute: () => void
  onToggleDeafen: () => void
  // Other-participant-only — local-only preferences, don't affect anyone else
  mutedForMe: boolean
  volume: number
  onToggleMuteForMe: () => void
  onVolumeChange: (volume: number) => void
}

export default function ParticipantSettingsMenu({
  participant,
  isSelf,
  isMuted,
  isDeafened,
  onToggleMute,
  onToggleDeafen,
  mutedForMe,
  volume,
  onToggleMuteForMe,
  onVolumeChange,
}: Props) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [open])

  const handleViewProfile = () => {
    setOpen(false)
    if (!buttonRef.current) return
    const rect = buttonRef.current.getBoundingClientRect()
    useUserOverlay.getState().open(participant.userId, rect, "VOICE_ROOM")
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        ref={buttonRef}
        onClick={() => setOpen((o) => !o)}
        title="Settings"
        className="p-1 rounded text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
      >
        <Settings size={13} />
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1 w-48 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-30 py-1">
          <button
            onClick={handleViewProfile}
            className="w-full flex items-center gap-2 px-3 py-1.5 text-left text-xs text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
          >
            <User size={13} />
            View Profile
          </button>

          {isSelf ? (
            <>
              <button
                onClick={() => { onToggleMute(); setOpen(false) }}
                className="w-full flex items-center gap-2 px-3 py-1.5 text-left text-xs text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
              >
                {isMuted ? <Mic size={13} /> : <MicOff size={13} />}
                {isMuted ? "Unmute" : "Mute"}
              </button>
              <button
                onClick={() => { onToggleDeafen(); setOpen(false) }}
                className="w-full flex items-center gap-2 px-3 py-1.5 text-left text-xs text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
              >
                {isDeafened ? <Headphones size={13} /> : <VolumeX size={13} />}
                {isDeafened ? "Undeafen" : "Deafen"}
              </button>
            </>
          ) : (
            <>
              <button
                onClick={onToggleMuteForMe}
                className="w-full flex items-center gap-2 px-3 py-1.5 text-left text-xs text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
              >
                {mutedForMe ? <Mic size={13} /> : <MicOff size={13} />}
                {mutedForMe ? "Unmute for me" : "Mute for me"}
              </button>
              <div className="px-3 py-1.5">
                <label className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 mb-1">
                  <Volume2 size={13} />
                  Volume
                </label>
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.05}
                  value={mutedForMe ? 0 : volume}
                  disabled={mutedForMe}
                  onChange={(e) => onVolumeChange(Number(e.target.value))}
                  className="w-full accent-green-500 disabled:opacity-40"
                />
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}
