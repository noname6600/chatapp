import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import {
  banMemberApi,
  bulkBanMembersApi,
  getBannedRoomMembersPaged,
  getRoomMembers,
  getRoomMembersPaged,
  kickMemberApi,
  transferOwnershipApi,
  unbanMemberApi,
  startPrivateChatApi,
} from "../api/room.service"
import { blockUserApi } from "../api/friend.service"
import UserAvatar from "../components/user/UserAvatar"
import { useUserStore } from "../store/user.store"
import { useChat } from "../store/chat.store"
import { useRooms } from "../store/room.store"
import type { RoomMember } from "../types/room"

const PAGE_SIZE = 20

type Tab = "members" | "banned"
type MenuAction = "block" | "kick" | "ban" | "transfer" | "bulk-ban"

type ConfirmationState = {
  open: boolean
  action: MenuAction | null
  userId: string | null
  title: string
  message: string
  confirmLabel: string
}

const EMPTY_CONFIRMATION: ConfirmationState = {
  open: false,
  action: null,
  userId: null,
  title: "",
  message: "",
  confirmLabel: "Confirm",
}

export default function RoomMemberManagementPage() {
  const { roomId = "" } = useParams()
  const navigate = useNavigate()
  const { setActiveRoom } = useChat()
  const { roomsById } = useRooms()

  const users = useUserStore((s) => s.users)
  const fetchUsers = useUserStore((s) => s.fetchUsers)

  const [tab, setTab] = useState<Tab>("members")
  const [query, setQuery] = useState("")
  const [page, setPage] = useState(0)

  const [members, setMembers] = useState<RoomMember[]>([])
  const [bannedUserIds, setBannedUserIds] = useState<string[]>([])

  const [shown, setShown] = useState(0)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(false)
  const [actionLoadingByUser, setActionLoadingByUser] = useState<Record<string, boolean>>({})

  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [menuUserId, setMenuUserId] = useState<string | null>(null)
  const [confirmation, setConfirmation] = useState<ConfirmationState>(EMPTY_CONFIRMATION)
  const [messageError, setMessageError] = useState("")
  const [confirmSubmitting, setConfirmSubmitting] = useState(false)
  const menuRef = useRef<HTMLDivElement | null>(null)

  const currentUserId = localStorage.getItem("my_user_id")
  const room = roomId ? roomsById[roomId] : null

  const [isOwner, setIsOwner] = useState(false)

  const refreshRole = useCallback(async () => {
    if (!roomId || !currentUserId) return

    const allMembers = await getRoomMembers(roomId)
    const me = allMembers.find((member) => member.userId === currentUserId)
    setIsOwner(me?.role === "OWNER")
  }, [roomId, currentUserId])

  const runAction = useCallback(async (userId: string, action: () => Promise<void>) => {
    setActionLoadingByUser((prev) => ({ ...prev, [userId]: true }))
    try {
      await action()
    } finally {
      setActionLoadingByUser((prev) => ({ ...prev, [userId]: false }))
    }
  }, [])

  const loadMembers = useCallback(async () => {
    if (!roomId) return

    setLoading(true)
    try {
      if (tab === "members") {
        const response = await getRoomMembersPaged(roomId, page, PAGE_SIZE, query)
        setMembers(response.members)
        setShown(response.shown)
        setTotal(response.total)
        setTotalPages(response.totalPages)

        await fetchUsers(response.members.map((member) => member.userId))
      } else {
        const response = await getBannedRoomMembersPaged(roomId, page, PAGE_SIZE)
        setBannedUserIds(response.userIds)
        setShown(response.shown)
        setTotal(response.total)
        setTotalPages(response.totalPages)

        await fetchUsers(response.userIds)
      }
    } finally {
      setLoading(false)
    }
  }, [fetchUsers, page, query, roomId, tab])

  useEffect(() => {
    void refreshRole().catch(() => {})
  }, [refreshRole])

  useEffect(() => {
    setPage(0)
    setSelectedIds(new Set())
    setMenuUserId(null)
    setMessageError("")
  }, [tab, query])

  useEffect(() => {
    const onMouseDown = (event: MouseEvent) => {
      if (!menuRef.current) return
      if (!menuRef.current.contains(event.target as Node)) {
        setMenuUserId(null)
      }
    }

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMenuUserId(null)
      }
    }

    document.addEventListener("mousedown", onMouseDown)
    document.addEventListener("keydown", onKeyDown)

    return () => {
      document.removeEventListener("mousedown", onMouseDown)
      document.removeEventListener("keydown", onKeyDown)
    }
  }, [])

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      void loadMembers().catch(() => {})
    }, 250)

    return () => window.clearTimeout(timeout)
  }, [loadMembers])

  const filteredMembers = useMemo(() => {
    if (tab !== "members" || !query.trim()) return members

    const normalized = query.trim().toLowerCase()

    return members.filter((member) => {
      const user = users[member.userId]
      const displayName = (user?.displayName || member.name || "").toLowerCase()
      const username = (user?.username || "").toLowerCase()
      return displayName.includes(normalized) || username.includes(normalized)
    })
  }, [members, query, tab, users])

  const toggleSelection = (userId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(userId)) {
        next.delete(userId)
      } else {
        next.add(userId)
      }
      return next
    })
  }

  const handleMessage = async (userId: string) => {
    setMessageError("")
    if (!currentUserId || userId === currentUserId) return
    const privateRoom = await startPrivateChatApi(userId)
    await setActiveRoom(privateRoom.id)
    navigate("/chat")
  }

  const handleBlock = async (userId: string) => {
    await runAction(userId, async () => {
      await blockUserApi(userId)
    })
  }

  const handleKick = async (userId: string) => {
    await runAction(userId, async () => {
      await kickMemberApi(roomId, userId)
      await loadMembers()
    })
  }

  const handleBan = async (userId: string) => {
    await runAction(userId, async () => {
      await banMemberApi(roomId, userId)
      await loadMembers()
    })
  }

  const handleTransferOwnership = async (userId: string) => {
    await runAction(userId, async () => {
      await transferOwnershipApi(roomId, userId)
      await refreshRole()
      await loadMembers()
    })
  }

  const handleUnban = async (userId: string) => {
    await runAction(userId, async () => {
      await unbanMemberApi(roomId, userId)
      await loadMembers()
    })
  }

  const handleBulkBan = async () => {
    const ids = Array.from(selectedIds)
    if (!ids.length) return

    try {
      await bulkBanMembersApi(roomId, ids)
      setSelectedIds(new Set())
      await loadMembers()
    } finally {
      setConfirmation(EMPTY_CONFIRMATION)
      setConfirmSubmitting(false)
    }
  }

  const openConfirmation = useCallback((action: MenuAction, userId: string | null = null) => {
    if (action === "block") {
      setConfirmation({
        open: true,
        action,
        userId,
        title: "Block this user?",
        message: "This action blocks direct interaction until manually unblocked.",
        confirmLabel: "Block User",
      })
      return
    }

    if (action === "kick") {
      setConfirmation({
        open: true,
        action,
        userId,
        title: "Kick this member?",
        message: "This member will be removed from the room immediately.",
        confirmLabel: "Kick Member",
      })
      return
    }

    if (action === "ban") {
      setConfirmation({
        open: true,
        action,
        userId,
        title: "Ban this member?",
        message: "This member will be removed from the room immediately and cannot rejoin via invite code or link until unbanned.",
        confirmLabel: "Ban Member",
      })
      return
    }

    if (action === "transfer") {
      setConfirmation({
        open: true,
        action,
        userId,
        title: "Transfer ownership?",
        message: "Ownership transfer is high impact and grants full moderation control to this member.",
        confirmLabel: "Transfer Ownership",
      })
      return
    }

    setConfirmation({
      open: true,
      action,
      userId,
      title: "Ban selected members?",
      message: "All selected members will be banned and removed from this room.",
      confirmLabel: "Ban Selected",
    })
  }, [])

  const handleConfirm = useCallback(async () => {
    if (!confirmation.action) return

    setConfirmSubmitting(true)

    try {
      if (confirmation.action === "bulk-ban") {
        await handleBulkBan()
        return
      }

      if (!confirmation.userId) return

      if (confirmation.action === "block") {
        await handleBlock(confirmation.userId)
      } else if (confirmation.action === "kick") {
        await handleKick(confirmation.userId)
      } else if (confirmation.action === "ban") {
        await handleBan(confirmation.userId)
      } else if (confirmation.action === "transfer") {
        await handleTransferOwnership(confirmation.userId)
      }

      setConfirmation(EMPTY_CONFIRMATION)
    } finally {
      setConfirmSubmitting(false)
    }
  }, [confirmation, handleBan, handleBlock, handleBulkBan, handleKick, handleTransferOwnership])

  if (!roomId) {
    return <div className="p-6 text-sm text-red-600">Missing room ID.</div>
  }

  return (
    <div className="h-full min-h-0 overflow-hidden bg-gradient-to-b from-slate-50 via-white to-slate-100/60">
      <div className="mx-auto flex h-full max-w-6xl flex-col px-4 py-4 md:px-6">
        <div className="mb-3 rounded-2xl border border-slate-200 bg-white/95 p-4 shadow-sm backdrop-blur">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500">Room Member Management</div>
              <div className="text-lg font-semibold text-slate-900">{room?.name || "Room"}</div>
            </div>
            <button
              type="button"
              onClick={() => navigate("/chat")}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100"
            >
              Back To Chat
            </button>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => setTab("members")}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${tab === "members" ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-700"}`}
            >
              Members
            </button>
            <button
              type="button"
              onClick={() => setTab("banned")}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${tab === "banned" ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-700"}`}
            >
              Banned
            </button>
          </div>

          {tab === "members" && (
            <div className="mt-3 flex w-full max-w-md items-center gap-2">
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search display name or username"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
              />
            </div>
          )}

          {messageError && (
            <div className="mt-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
              {messageError}
            </div>
          )}
        </div>

        <div className="relative min-h-0 flex-1 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="grid grid-cols-[52px_minmax(170px,1fr)_120px_68px] gap-3 border-b border-slate-200 bg-slate-100 px-4 py-3 text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-600 md:grid-cols-[52px_minmax(220px,1fr)_180px_68px]">
            <div>Avatar</div>
            <div>Member</div>
            <div>Join Day</div>
            <div>More</div>
          </div>

          <div className="h-[calc(100%-56px)] overflow-y-auto">
            {loading ? (
              <div className="p-6 text-sm text-slate-500">Loading members...</div>
            ) : tab === "members" ? (
              <div className="divide-y divide-slate-100">
                {filteredMembers.map((member) => {
                  const user = users[member.userId]
                  const displayName = user?.displayName || member.name || "Unknown"
                  const username = user?.username || member.userId
                  const joined = member.joinedAt ? new Date(member.joinedAt).toLocaleDateString() : "-"
                  const disabled = actionLoadingByUser[member.userId]
                  const isSelf = member.userId === currentUserId
                  const canModerate = isOwner && member.role !== "OWNER"
                  const menuOpen = menuUserId === member.userId

                  return (
                    <div key={member.userId} className="relative grid grid-cols-[52px_minmax(170px,1fr)_120px_68px] gap-3 items-center px-4 py-3 md:grid-cols-[52px_minmax(220px,1fr)_180px_68px]">
                      <div className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={selectedIds.has(member.userId)}
                          onChange={() => toggleSelection(member.userId)}
                          disabled={!canModerate || isSelf}
                          aria-label={`Select ${displayName}`}
                        />
                        <UserAvatar userId={member.userId} avatar={user?.avatarUrl || member.avatarUrl} size={28} />
                      </div>

                      <div className="min-w-0">
                        <div className="truncate text-sm font-semibold text-slate-800">{displayName}</div>
                        <div className="truncate text-xs text-slate-500">@{username}</div>
                      </div>

                      <div className="text-sm text-slate-600">{joined}</div>

                      <div className="relative flex items-center justify-end" ref={menuOpen ? menuRef : undefined}>
                        <button
                          type="button"
                          disabled={disabled}
                          onClick={() => setMenuUserId((prev) => (prev === member.userId ? null : member.userId))}
                          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-300 text-slate-600 transition-colors hover:bg-slate-100 disabled:opacity-40"
                          aria-label={`More actions for ${displayName}`}
                        >
                          <span aria-hidden>...</span>
                        </button>

                        {menuOpen && (
                          <div className="absolute right-0 top-10 z-20 w-44 rounded-xl border border-slate-200 bg-white p-1 shadow-lg">
                            <MenuItem
                              disabled={disabled || isSelf}
                              label="Message"
                              onClick={() => {
                                setMenuUserId(null)
                                void handleMessage(member.userId).catch((error) => {
                                  setMessageError(error instanceof Error ? error.message : "Unable to open private chat.")
                                })
                              }}
                            />
                            <MenuItem
                              disabled={disabled || isSelf}
                              label="Block"
                              onClick={() => {
                                setMenuUserId(null)
                                openConfirmation("block", member.userId)
                              }}
                            />
                            <MenuItem
                              disabled={disabled || !canModerate || isSelf}
                              label="Kick"
                              onClick={() => {
                                setMenuUserId(null)
                                openConfirmation("kick", member.userId)
                              }}
                            />
                            <MenuItem
                              disabled={disabled || !canModerate || isSelf}
                              label="Ban"
                              onClick={() => {
                                setMenuUserId(null)
                                openConfirmation("ban", member.userId)
                              }}
                            />
                            <MenuItem
                              disabled={disabled || !canModerate || isSelf}
                              label="Transfer"
                              onClick={() => {
                                setMenuUserId(null)
                                openConfirmation("transfer", member.userId)
                              }}
                            />
                          </div>
                        )}
                      </div>
                    </div>
                  )
                })}

                {!filteredMembers.length && (
                  <div className="p-8 text-center text-sm text-slate-400">No members found.</div>
                )}
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {bannedUserIds.map((userId) => {
                  const user = users[userId]
                  const displayName = user?.displayName || "Unknown"
                  const username = user?.username || userId
                  const disabled = actionLoadingByUser[userId]

                  return (
                    <div key={userId} className="grid grid-cols-[52px_minmax(170px,1fr)_120px_68px] gap-3 items-center px-4 py-3 md:grid-cols-[52px_minmax(220px,1fr)_180px_68px]">
                      <div>
                        <UserAvatar userId={userId} avatar={user?.avatarUrl} size={28} />
                      </div>
                      <div>
                        <div className="truncate text-sm font-semibold text-slate-800">{displayName}</div>
                        <div className="truncate text-xs text-slate-500">@{username}</div>
                      </div>
                      <div className="text-sm text-slate-600">Banned</div>
                      <div className="flex justify-end">
                        <button
                          type="button"
                          disabled={disabled || !isOwner}
                          onClick={() => void handleUnban(userId)}
                          className="rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-100 disabled:opacity-40"
                        >
                          Unban
                        </button>
                      </div>
                    </div>
                  )
                })}

                {!bannedUserIds.length && (
                  <div className="p-8 text-center text-sm text-slate-400">No banned users.</div>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="mt-3 flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
          <div className="font-medium text-slate-700">{shown} / total {total}</div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPage((prev) => Math.max(0, prev - 1))}
              disabled={page <= 0}
              className="rounded-md border border-slate-300 px-2 py-1 text-slate-700 disabled:opacity-40"
            >
              Prev
            </button>
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">Page {page + 1} / {Math.max(totalPages, 1)}</span>
            <button
              type="button"
              onClick={() => setPage((prev) => (prev + 1 < totalPages ? prev + 1 : prev))}
              disabled={page + 1 >= totalPages}
              className="rounded-md border border-slate-300 px-2 py-1 text-slate-700 disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      </div>

      {tab === "members" && selectedIds.size > 0 && (
        <div className="fixed bottom-6 left-1/2 z-40 -translate-x-1/2 rounded-full border border-rose-300 bg-rose-50 px-4 py-3 shadow-lg">
          <div className="mb-2 text-center text-xs font-semibold text-rose-700">{selectedIds.size} selected</div>
          <button
            type="button"
            onClick={() => openConfirmation("bulk-ban")}
            className="rounded-md bg-rose-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-rose-700"
          >
            Ban Selected
          </button>
        </div>
      )}

      <WarningModal
        open={confirmation.open}
        title={confirmation.title}
        message={confirmation.message}
        confirmLabel={confirmation.confirmLabel}
        submitting={confirmSubmitting}
        onCancel={() => {
          if (confirmSubmitting) return
          setConfirmation(EMPTY_CONFIRMATION)
        }}
        onConfirm={() => void handleConfirm()}
      />
    </div>
  )
}

function MenuItem({
  label,
  disabled,
  onClick,
}: {
  label: string
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="flex w-full items-center rounded-lg px-3 py-2 text-left text-xs font-medium text-slate-700 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"
    >
      {label}
    </button>
  )
}

function WarningModal({
  open,
  title,
  message,
  confirmLabel,
  submitting,
  onCancel,
  onConfirm,
}: {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  submitting: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 px-4">
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-5 shadow-xl">
        <div className="text-sm font-semibold uppercase tracking-[0.12em] text-rose-600">Warning</div>
        <h3 className="mt-1 text-lg font-semibold text-slate-900">{title}</h3>
        <p className="mt-2 text-sm text-slate-600">{message}</p>

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100 disabled:opacity-40"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={submitting}
            className="rounded-md bg-rose-600 px-3 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-rose-700 disabled:opacity-50"
          >
            {submitting ? "Working..." : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
