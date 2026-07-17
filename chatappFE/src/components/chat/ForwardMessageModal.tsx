import { useEffect, useMemo, useState } from "react"
import { X, FileText, Video, Users, User, Check } from "lucide-react"
import { Button } from "../ui/Button"
import type { ChatMessage, Attachment } from "../../types/message"
import type { Room } from "../../types/room"
import type { UserProfile } from "../../types/user"

// ─── message preview ────────────────────────────────────────────────────────

function summarizeAttachments(attachments: Attachment[]): string {
  const imageCount = attachments.filter((a) => a.type === "IMAGE").length
  const videoCount = attachments.filter((a) => a.type === "VIDEO").length
  const fileCount  = attachments.filter((a) => a.type === "FILE").length
  if (imageCount > 0) return imageCount > 1 ? `${imageCount} images` : "Image"
  if (videoCount > 0) return videoCount > 1 ? `${videoCount} videos` : "Video"
  if (fileCount  > 0) return fileCount  > 1 ? `${fileCount} files`   : "File"
  return "Attachment"
}

function ForwardPreview({ message }: { message: ChatMessage }) {
  if (message.deleted) {
    return <span className="italic text-gray-400">This message was deleted</span>
  }

  const text        = message.content?.trim() ?? ""
  const attachments = message.attachments ?? []
  const images      = attachments.filter((a) => a.type === "IMAGE")
  const videos      = attachments.filter((a) => a.type === "VIDEO")
  const files       = attachments.filter((a) => a.type === "FILE")
  const thumbnail   = images[0]?.url

  if (thumbnail) {
    return (
      <div className="flex items-center gap-2.5">
        <img
          src={thumbnail}
          alt=""
          className="h-10 w-10 rounded object-cover flex-shrink-0 border border-gray-200"
        />
        <span className="truncate text-gray-800">
          {text || summarizeAttachments(attachments)}
        </span>
      </div>
    )
  }
  if (videos.length > 0 && !text) {
    return (
      <div className="flex items-center gap-2 text-gray-700">
        <Video size={15} className="flex-shrink-0 text-gray-400" />
        <span className="truncate">{videos[0].fileName ?? "Video"}</span>
      </div>
    )
  }
  if (files.length > 0 && !text) {
    return (
      <div className="flex items-center gap-2 text-gray-700">
        <FileText size={15} className="flex-shrink-0 text-gray-400" />
        <span className="truncate">{files[0].fileName ?? "File"}</span>
      </div>
    )
  }
  if (text) return <span className="truncate text-gray-800">{text}</span>
  return <span className="italic text-gray-400">Attachment</span>
}

// ─── avatar helpers ──────────────────────────────────────────────────────────

function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? "")
    .join("")
}

const GROUP_COLORS = [
  "bg-violet-500", "bg-blue-500", "bg-emerald-500",
  "bg-amber-500",  "bg-rose-500", "bg-cyan-500",
]

function groupColor(id: string): string {
  let hash = 0
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) >>> 0
  return GROUP_COLORS[hash % GROUP_COLORS.length]
}

// ─── room row ────────────────────────────────────────────────────────────────

interface RoomRowProps {
  room: Room
  user: UserProfile | undefined
  selected: boolean
  onToggle: () => void
}

function RoomRow({ room, user, selected, onToggle }: RoomRowProps) {
  const isDm = room.type === "PRIVATE"

  return (
    <button
      type="button"
      onClick={onToggle}
      className={`w-full flex items-center gap-3 px-4 py-2.5 text-left transition-colors ${
        selected ? "bg-blue-50" : "hover:bg-gray-50"
      }`}
    >
      {/* avatar */}
      {isDm ? (
        user?.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt=""
            className="h-9 w-9 rounded-full object-cover flex-shrink-0 ring-1 ring-gray-200"
          />
        ) : (
          <div className="h-9 w-9 rounded-full flex-shrink-0 bg-gray-200 flex items-center justify-center">
            <User size={16} className="text-gray-500" />
          </div>
        )
      ) : (
        <div
          className={`h-9 w-9 rounded-full flex-shrink-0 flex items-center justify-center text-white text-xs font-semibold ${groupColor(room.id)}`}
        >
          {room.name ? initials(room.name) : <Users size={15} />}
        </div>
      )}

      {/* name + meta */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          <span className="text-sm font-medium text-gray-900 truncate">
            {isDm ? (user?.displayName || user?.username || room.name) : room.name}
          </span>
          {isDm ? (
            <span className="flex-shrink-0 text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-500 leading-none">
              DM
            </span>
          ) : (
            <span className="flex-shrink-0 text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-violet-100 text-violet-600 leading-none">
              Group
            </span>
          )}
        </div>
        {isDm && user?.username && (
          <div className="text-xs text-gray-400 truncate">@{user.username}</div>
        )}
      </div>

      {/* checkbox */}
      <div
        className={`h-5 w-5 flex-shrink-0 rounded-full border-2 flex items-center justify-center transition-colors ${
          selected
            ? "bg-blue-500 border-blue-500"
            : "border-gray-300 bg-white"
        }`}
      >
        {selected && <Check size={11} strokeWidth={3} className="text-white" />}
      </div>
    </button>
  )
}

// ─── modal ───────────────────────────────────────────────────────────────────

interface ForwardMessageModalProps {
  open: boolean
  sourceMessage: ChatMessage | null
  currentRoomId: string
  rooms: Room[]
  users: Record<string, UserProfile>
  onClose: () => void
  onConfirm: (targetRoomIds: string[]) => Promise<void>
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
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setSelectedIds(new Set())
    setQuery("")
  }, [open])

  const toggle = (id: string) =>
    setSelectedIds((prev) => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })

  const { people, groups } = useMemo(() => {
    const q = query.trim().toLowerCase()
    const candidates = rooms.filter((room) => {
      if (room.id === currentRoomId) return false
      if (!q) return true
      const roomName   = (room.name || "").toLowerCase()
      const profile    = room.otherUserId ? users[room.otherUserId] : undefined
      const displayName = (profile?.displayName || "").toLowerCase()
      const username   = (profile?.username || "").toLowerCase()
      return (
        roomName.includes(q) ||
        displayName.includes(q) ||
        username.includes(q) ||
        `@${username}`.includes(q)
      )
    })
    return {
      people: candidates.filter((r) => r.type === "PRIVATE"),
      groups: candidates.filter((r) => r.type !== "PRIVATE"),
    }
  }, [currentRoomId, query, rooms, users])

  if (!open || !sourceMessage) return null

  const handleConfirm = async () => {
    if (selectedIds.size === 0 || loading) return
    setLoading(true)
    try {
      await onConfirm([...selectedIds])
      setSelectedIds(new Set())
      onClose()
    } finally {
      setLoading(false)
    }
  }

  const totalCandidates = people.length + groups.length
  const count = selectedIds.size

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-xl bg-white shadow-xl border border-gray-200 overflow-hidden flex flex-col max-h-[90vh]">

        {/* header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 flex-shrink-0">
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

        {/* message preview */}
        <div className="px-4 py-2.5 border-b border-gray-100 flex-shrink-0">
          <div className="text-xs text-gray-400 mb-1">Forwarding</div>
          <div className="text-sm rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 flex items-center min-w-0">
            <ForwardPreview message={sourceMessage} />
          </div>
        </div>

        {/* search */}
        <div className="px-4 py-2.5 border-b border-gray-100 flex-shrink-0">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search people or groups…"
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* list */}
        <div className="overflow-y-auto flex-1">
          {totalCandidates === 0 ? (
            <div className="px-4 py-8 text-sm text-gray-400 text-center">No matches found.</div>
          ) : (
            <>
              {people.length > 0 && (
                <>
                  <div className="flex items-center gap-1.5 px-4 pt-3 pb-1">
                    <User size={12} className="text-gray-400" />
                    <span className="text-xs font-semibold text-gray-400 uppercase tracking-wide">
                      People
                    </span>
                  </div>
                  {people.map((room) => (
                    <RoomRow
                      key={room.id}
                      room={room}
                      user={room.otherUserId ? users[room.otherUserId] : undefined}
                      selected={selectedIds.has(room.id)}
                      onToggle={() => toggle(room.id)}
                    />
                  ))}
                </>
              )}

              {groups.length > 0 && (
                <>
                  <div className="flex items-center gap-1.5 px-4 pt-3 pb-1">
                    <Users size={12} className="text-gray-400" />
                    <span className="text-xs font-semibold text-gray-400 uppercase tracking-wide">
                      Groups
                    </span>
                  </div>
                  {groups.map((room) => (
                    <RoomRow
                      key={room.id}
                      room={room}
                      user={undefined}
                      selected={selectedIds.has(room.id)}
                      onToggle={() => toggle(room.id)}
                    />
                  ))}
                </>
              )}

              <div className="pb-2" />
            </>
          )}
        </div>

        {/* selected chips */}
        {count > 0 && (
          <div className="px-4 py-2 border-t border-gray-100 flex-shrink-0 bg-blue-50/60">
            <p className="text-xs text-blue-600 font-medium">
              {count} {count === 1 ? "recipient" : "recipients"} selected
            </p>
          </div>
        )}

        {/* footer */}
        <div className="px-4 py-3 border-t border-gray-100 flex-shrink-0 flex justify-end gap-2">
          <Button variant="outline" size="sm" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button
            size="sm"
            onClick={() => void handleConfirm()}
            disabled={count === 0 || loading}
          >
            {loading
              ? "Forwarding…"
              : count > 0
                ? `Forward to ${count}`
                : "Forward"}
          </Button>
        </div>
      </div>
    </div>
  )
}
