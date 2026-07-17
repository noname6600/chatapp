import { useEffect, useRef } from "react"
import type { RemoteTrack } from "livekit-client"

interface Props {
  tracks: RemoteTrack[]
}

function ScreenTile({ track }: { track: RemoteTrack }) {
  const videoRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    const el = videoRef.current
    if (!el) return
    track.attach(el)
    return () => { track.detach(el) }
  }, [track])

  return (
    <video
      ref={videoRef}
      autoPlay
      playsInline
      muted
      className="max-w-full max-h-full rounded-lg shadow-lg object-contain"
    />
  )
}

export default function ScreenShareOverlay({ tracks }: Props) {
  if (tracks.length === 0) return null

  return (
    <div className="fixed inset-0 z-30 pointer-events-none flex flex-col gap-2 items-end justify-end p-4">
      {tracks.map((track) => (
        <div
          key={track.sid}
          className="pointer-events-auto bg-black rounded-lg overflow-hidden"
          style={{ width: "min(480px, 40vw)", height: "min(270px, 22.5vw)" }}
        >
          <ScreenTile track={track} />
        </div>
      ))}
    </div>
  )
}
