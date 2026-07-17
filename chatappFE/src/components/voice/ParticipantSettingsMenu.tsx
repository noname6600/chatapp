import { useEffect, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { User, Mic, MicOff, Headphones, VolumeX, Volume2, Settings } from "lucide-react"
import { useUserOverlay } from "../../store/userOverlay.store"
import type { VoiceParticipant } from "../../api/voice.service"

const MENU_WIDTH = 192 // matches the old w-48

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
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  // Rendered via a portal straight into <body> — this list lives inside a
  // scrollable sidebar, and an absolutely-positioned popover nested inside
  // that gets clipped by the sidebar's own overflow instead of floating
  // above everything like a menu should.
  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (buttonRef.current?.contains(target)) return
      if (menuRef.current?.contains(target)) return
      setOpen(false)
    }
    // A fixed-position portal doesn't track its trigger on scroll — close
    // instead of letting it drift away from the button that opened it.
    const handleScroll = () => setOpen(false)
    document.addEventListener("mousedown", handleClickOutside)
    window.addEventListener("scroll", handleScroll, true)
    return () => {
      document.removeEventListener("mousedown", handleClickOutside)
      window.removeEventListener("scroll", handleScroll, true)
    }
  }, [open])

  const handleToggle = () => {
    if (!open && buttonRef.current) {
      const rect = buttonRef.current.getBoundingClientRect()
      setPosition({ top: rect.bottom + 4, left: Math.max(8, rect.right - MENU_WIDTH) })
    }
    setOpen((o) => !o)
  }

  const handleViewProfile = () => {
    setOpen(false)
    if (!buttonRef.current) return
    const rect = buttonRef.current.getBoundingClientRect()
    useUserOverlay.getState().open(participant.userId, rect, "VOICE_ROOM")
  }

  return (
    <>
      <button
        ref={buttonRef}
        onClick={handleToggle}
        title="Settings"
        className="p-1 rounded text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
      >
        <Settings size={13} />
      </button>

      {open && position && createPortal(
        <div
          ref={menuRef}
          style={{ position: "fixed", top: position.top, left: position.left, width: MENU_WIDTH }}
          className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-[100] py-1"
        >
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
        </div>,
        document.body
      )}
    </>
  )
}
