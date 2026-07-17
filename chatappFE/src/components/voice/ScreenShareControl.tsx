import { useEffect, useRef, useState } from "react"
import { Monitor, MonitorOff } from "lucide-react"
import type { ScreenShareQuality } from "../../hooks/useVoiceRoom"

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
  const [audio, setAudio] = useState(false)
  const [quality, setQuality] = useState<ScreenShareQuality>("auto")
  const containerRef = useRef<HTMLDivElement>(null)

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

  const handleClick = () => {
    if (isScreenSharing) {
      toggleScreenShare()
      return
    }
    setOpen((o) => !o)
  }

  const handleStart = () => {
    setOpen(false)
    toggleScreenShare({ audio, quality })
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={handleClick}
        title={isScreenSharing ? "Stop sharing" : "Share screen"}
        className={buttonClassName}
      >
        {isScreenSharing ? <MonitorOff size={iconSize} /> : <Monitor size={iconSize} />}
      </button>

      {open && !isScreenSharing && (
        <div className="absolute right-0 top-full mt-1 w-56 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-30 p-3 space-y-3">
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
        </div>
      )}
    </div>
  )
}
