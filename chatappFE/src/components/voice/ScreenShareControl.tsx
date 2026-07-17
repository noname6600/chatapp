import { useEffect, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { Monitor, MonitorOff } from "lucide-react"
import type { ScreenShareQuality } from "../../hooks/useVoiceRoom"

const PANEL_WIDTH = 224 // matches the old w-56

interface Props {
  isScreenSharing: boolean
  toggleScreenShare: (options?: { audio?: boolean; quality?: ScreenShareQuality }) => Promise<void>
  buttonClassName: string
  iconSize?: number
}

export default function ScreenShareControl({
  isScreenSharing,
  toggleScreenShare,
  buttonClassName,
  iconSize = 14,
}: Props) {
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null)
  const [audio, setAudio] = useState(false)
  const [quality, setQuality] = useState<ScreenShareQuality>("auto")
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  // Portal straight into <body>, same reasoning as ParticipantSettingsMenu —
  // this button lives inside scrollable/clipped control bars, so an
  // absolutely-positioned popover would get cut off instead of floating
  // above the page.
  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (buttonRef.current?.contains(target)) return
      if (panelRef.current?.contains(target)) return
      setOpen(false)
    }
    const handleScroll = () => setOpen(false)
    document.addEventListener("mousedown", handleClickOutside)
    window.addEventListener("scroll", handleScroll, true)
    return () => {
      document.removeEventListener("mousedown", handleClickOutside)
      window.removeEventListener("scroll", handleScroll, true)
    }
  }, [open])

  const handleClick = () => {
    if (isScreenSharing) {
      toggleScreenShare()
      return
    }
    if (!open && buttonRef.current) {
      const rect = buttonRef.current.getBoundingClientRect()
      setPosition({ top: rect.bottom + 4, left: Math.max(8, rect.right - PANEL_WIDTH) })
    }
    setOpen((o) => !o)
  }

  const handleStart = () => {
    setOpen(false)
    toggleScreenShare({ audio, quality })
  }

  return (
    <>
      <button
        ref={buttonRef}
        onClick={handleClick}
        title={isScreenSharing ? "Stop sharing" : "Share screen"}
        className={buttonClassName}
      >
        {isScreenSharing ? <MonitorOff size={iconSize} /> : <Monitor size={iconSize} />}
      </button>

      {open && !isScreenSharing && position && createPortal(
        <div
          ref={panelRef}
          style={{ position: "fixed", top: position.top, left: position.left, width: PANEL_WIDTH }}
          className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-[100] p-3 space-y-3"
        >
          <div>
            <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">Quality</label>
            <select
              value={quality}
              onChange={(e) => setQuality(e.target.value as ScreenShareQuality)}
              className="w-full text-xs border border-gray-200 dark:border-gray-600 rounded px-2 py-1 bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-200"
            >
              <option value="auto">Auto</option>
              <option value="high">High</option>
              <option value="medium">Medium</option>
              <option value="low">Low</option>
            </select>
          </div>
          <label className="flex items-center gap-2 text-xs text-gray-600 dark:text-gray-300">
            <input type="checkbox" checked={audio} onChange={(e) => setAudio(e.target.checked)} />
            Include audio
          </label>
          <button
            onClick={handleStart}
            className="w-full py-1.5 rounded-lg bg-green-500 hover:bg-green-600 text-white text-xs font-medium transition-colors"
          >
            Start Sharing
          </button>
        </div>,
        document.body
      )}
    </>
  )
}
