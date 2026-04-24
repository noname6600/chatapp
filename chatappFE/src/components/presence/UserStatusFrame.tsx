import clsx from "clsx"
import type { PresenceStatus } from "../../types/presence"
import { getStatusDotClass } from "../../utils/presenceStatus"

interface UserStatusFrameProps {
  status: PresenceStatus
  size: number
  children: React.ReactNode
  className?: string
  borderWidth?: number
}

export default function UserStatusFrame({
  status,
  size,
  children,
  className,
  borderWidth = 0,
}: UserStatusFrameProps) {
  const dotSize = Math.max(10, Math.min(16, Math.round(size / 3.2)))
  const dotOffset = -Math.round(dotSize / 5) + borderWidth

  return (
    <span className={clsx("relative inline-flex", className)}>
      {children}
      <span
        aria-hidden="true"
        className={clsx(
          "pointer-events-none absolute rounded-full border-2 border-white",
          getStatusDotClass(status)
        )}
        style={{
          width: `${dotSize}px`,
          height: `${dotSize}px`,
          right: `${dotOffset}px`,
          bottom: `${dotOffset}px`,
        }}
      />
    </span>
  )
}
