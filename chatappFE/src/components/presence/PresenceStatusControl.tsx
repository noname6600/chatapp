import { useState } from "react"

import { updateMyPresenceApi } from "../../api/presence.service"
import { usePresenceStore } from "../../store/presence.store"
import { useAuth } from "../../store/auth.store"

import type { PresenceMode, PresenceStatus } from "../../types/presence"

type SelectValue = PresenceMode | PresenceStatus

const optionLabels: Record<SelectValue, string> = {
  AUTO: "Auto",
  MANUAL: "Manual",
  ONLINE: "Online",
  AWAY: "Away",
  OFFLINE: "Offline",
}

export default function PresenceStatusControl() {
  const { userId } = useAuth()
  const selfPresence = usePresenceStore((s) => s.selfPresence)
  const setSelfPresence = usePresenceStore((s) => s.setSelfPresence)
  const setUserStatus = usePresenceStore((s) => s.setUserStatus)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const value: SelectValue =
    selfPresence?.mode === "MANUAL"
      ? selfPresence.manualStatus ?? selfPresence.effectiveStatus
      : "AUTO"

  const handleChange = async (nextValue: SelectValue) => {
    setIsSaving(true)
    setError(null)

    try {
      const nextPresence =
        nextValue === "AUTO"
          ? await updateMyPresenceApi({ mode: "AUTO" })
          : await updateMyPresenceApi({ mode: "MANUAL", status: nextValue as PresenceStatus })

      setSelfPresence(nextPresence)

      if (userId) {
        setUserStatus(userId, nextPresence.effectiveStatus)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update presence")
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">
        Status
      </label>
      <select
        value={value}
        disabled={isSaving}
        onChange={(event) => void handleChange(event.target.value as SelectValue)}
        className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 shadow-sm outline-none transition focus:border-blue-400"
      >
        {(["AUTO", "ONLINE", "AWAY", "OFFLINE"] as SelectValue[]).map((option) => (
          <option key={option} value={option}>
            {optionLabels[option]}
          </option>
        ))}
      </select>
      {selfPresence && (
        <span className="text-xs text-gray-500">
          Current: {selfPresence.effectiveStatus.toLowerCase()}
        </span>
      )}
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  )
}