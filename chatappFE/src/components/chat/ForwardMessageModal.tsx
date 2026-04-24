import { useMemo, useState } from "react"
import { X } from "lucide-react"
import { Button } from "../ui/Button"
import type { ChatMessage } from "../../types/message"
import type { Room } from "../../types/room"
import type { UserProfile } from "../../types/user"

interface ForwardMessageModalProps {
  open: boolean
  sourceMessage: ChatMessage | null
  currentRoomId: string
  rooms: Room[]
  users: Record<string, UserProfile>
  onClose: () => void
  onConfirm: (targetRoomId: string) => Promise<void>
}

export default function ForwardMessageModal({
  open,
  sourceMessage,
  currentRoomId,
  rooms,
  users,
  onClose,
  onConfirm,
}: ForwardMessageModalProps) {
  const [query, setQuery] = useState("")
  const [targetRoomId, setTargetRoomId] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const candidates = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()

    return rooms.filter((room) => {
      if (room.id === currentRoomId) return false

      const roomName = (room.name || "").toLowerCase()
      const otherUserName = room.otherUserId
        ? (users[room.otherUserId]?.displayName || users[room.otherUserId]?.username || "").toLowerCase()
        : ""
      const handle = room.otherUserId
        ? `@${(users[room.otherUserId]?.username || "").toLowerCase()}`
        : ""

      if (!normalizedQuery) return true
      return (
        roomName.includes(normalizedQuery) ||
        otherUserName.includes(normalizedQuery) ||
        handle.includes(normalizedQuery)
      )
    })
  }, [currentRoomId, query, rooms, users])

  if (!open || !sourceMessage) return null

  const preview = sourceMessage.content?.trim() || "(attachment or structured content)"

  const handleConfirm = async () => {
    if (!targetRoomId || loading) return
    setLoading(true)
    try {
      await onConfirm(targetRoomId)
      onClose()
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-lg rounded-xl bg-white shadow-xl border border-gray-200 overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">Forward message</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1.5 text-gray-500 hover:bg-gray-100"
            aria-label="Close"
          >
            <X size={16} />
          </button>
        </div>

        <div className="px-4 py-3 border-b border-gray-100">
          <div className="text-xs text-gray-500 mb-1">Message preview</div>
          <div className="text-sm text-gray-800 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 truncate">
            {preview}
          </div>
        </div>

        <div className="px-4 py-3 space-y-3">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search groups or @username"
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />

          <div className="max-h-64 overflow-y-auto rounded-lg border border-gray-200 divide-y divide-gray-100">
            {candidates.length === 0 ? (
              <div className="px-3 py-5 text-sm text-gray-500 text-center">No matches found.</div>
            ) : (
              candidates.map((room) => {
                const active = targetRoomId === room.id
                return (
                  <button
                    key={room.id}
                    type="button"
                    className={`w-full px-3 py-2 text-left transition ${active ? "bg-blue-50" : "hover:bg-gray-50"}`}
                    onClick={() => setTargetRoomId(room.id)}
                  >
                    <div className="text-sm font-medium text-gray-900">{room.name}</div>
                    {room.otherUserId && users[room.otherUserId]?.username && (
                      <div className="text-xs text-gray-500">@{users[room.otherUserId]?.username}</div>
                    )}
                  </button>
                )
              })
            )}
          </div>
        </div>

        <div className="px-4 py-3 border-t border-gray-100 flex justify-end gap-2">
          <Button variant="outline" size="sm" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button size="sm" onClick={() => void handleConfirm()} disabled={!targetRoomId || loading}>
            {loading ? "Forwarding..." : "Forward"}
          </Button>
        </div>
      </div>
    </div>
  )
}
