import { Check, Copy, Download, FileText, Image as ImageIcon, Link2, Video } from "lucide-react"
import { useState } from "react"
import type { ReactNode } from "react"

import type { Attachment, MessageBlock } from "../../types/message"
import { useInviteJoin } from "../../hooks/useInviteJoin"
import { useRooms } from "../../store/room.store"
import Username from "../user/Username"

interface MessageBlocksProps {
  blocks: MessageBlock[]
  resolveMentionLabel?: (token: string) => string
  resolveMentionUserId?: (token: string) => string | null
}

export function getRenderableBlocks(blocks: MessageBlock[] = []): MessageBlock[] {
  return blocks.filter((block) => {
    if (block.type === "TEXT") {
      return Boolean(block.text?.trim())
    }

    if (block.type === "ROOM_INVITE") {
      return Boolean(block.roomInvite?.roomId)
    }

    return Boolean(block.attachment)
  })
}

const URL_REGEX = /(https?:\/\/[^\s]+)/g
const MENTION_REGEX = /(^|\s)(@[A-Za-z0-9_.-]+)/g

function renderTextWithMentions(
  text: string,
  keyPrefix: string,
  resolveMentionLabel?: (token: string) => string,
  resolveMentionUserId?: (token: string) => string | null
) {
  const nodes: ReactNode[] = []
  let cursor = 0
  let mentionIndex = 0

  for (const match of text.matchAll(MENTION_REGEX)) {
    const matchIndex = match.index ?? 0
    const leading = match[1] ?? ""
    const mention = match[2] ?? ""
    const mentionToken = mention.startsWith("@") ? mention.slice(1) : mention
    const mentionLabel = resolveMentionLabel
      ? `@${resolveMentionLabel(mentionToken)}`
      : mention
    const mentionUserId = resolveMentionUserId?.(mentionToken) ?? null
    const mentionStart = matchIndex + leading.length

    if (matchIndex > cursor) {
      nodes.push(
        <span key={`${keyPrefix}-text-${mentionIndex}-${cursor}`}>
          {text.slice(cursor, matchIndex)}
        </span>
      )
    }

    if (leading.length > 0) {
      nodes.push(
        <span key={`${keyPrefix}-lead-${mentionIndex}-${matchIndex}`}>
          {leading}
        </span>
      )
    }

    const mentionNode = (
      <span className="rounded bg-amber-100/80 px-0.5 font-medium text-amber-800">
        {mentionLabel}
      </span>
    )

    nodes.push(
      mentionUserId ? (
        <Username key={`${keyPrefix}-mention-${mentionIndex}`} userId={mentionUserId}>
          {mentionNode}
        </Username>
      ) : (
        <span key={`${keyPrefix}-mention-${mentionIndex}`}>{mentionNode}</span>
      )
    )

    cursor = mentionStart + mention.length
    mentionIndex += 1
  }

  if (cursor < text.length) {
    nodes.push(
      <span key={`${keyPrefix}-tail-${cursor}`}>{text.slice(cursor)}</span>
    )
  }

  if (nodes.length === 0) {
    return <span>{text}</span>
  }

  return nodes
}

function renderTextWithLinks(
  text: string,
  resolveMentionLabel?: (token: string) => string,
  resolveMentionUserId?: (token: string) => string | null
) {
  return text.split(URL_REGEX).map((part, index) => {
    if (!part) {
      return null
    }

    if (/^https?:\/\/[^\s]+$/.test(part)) {
      return (
        <a
          key={`${part}-${index}`}
          href={part}
          target="_blank"
          rel="noopener noreferrer"
          className="text-blue-600 underline underline-offset-2 break-all"
        >
          {part}
        </a>
      )
    }

    return (
      <span key={`${part}-${index}`}>
        {renderTextWithMentions(
          part,
          `part-${index}`,
          resolveMentionLabel,
          resolveMentionUserId
        )}
      </span>
    )
  })
}

function getAttachmentIcon(attachment: Attachment) {
  switch (attachment.type) {
    case "IMAGE":
      return <ImageIcon size={40} />
    case "VIDEO":
      return <Video size={40} />
    case "FILE":
      return <FileText size={40} />
    default:
      return <FileText size={40} />
  }
}

function AssetBlock({ attachment }: { attachment: Attachment }) {
  if (attachment.type === "IMAGE") {
    return (
      <a
        href={attachment.url}
        target="_blank"
        rel="noopener noreferrer"
        className="block max-w-xs"
      >
        <img
          src={attachment.url}
          alt={attachment.fileName || attachment.name || "Image"}
          className="rounded-lg max-h-72 object-cover hover:opacity-90 transition"
        />
      </a>
    )
  }

  return (
    <a
      href={attachment.url}
      target="_blank"
      rel="noopener noreferrer"
      className="flex items-center gap-3 p-2 rounded-lg bg-gray-50 hover:bg-gray-100 transition border border-gray-200 max-w-xs"
    >
      <div className="text-gray-600 flex-shrink-0">{getAttachmentIcon(attachment)}</div>
      <div className="min-w-0 flex-1">
        <div className="text-sm font-medium text-gray-900 truncate">
          {attachment.fileName || attachment.name || "file"}
        </div>
        <div className="text-xs text-gray-500">
          {attachment.size ? `${(attachment.size / 1024).toFixed(2)} KB` : ""}
        </div>
      </div>
      <Download size={16} className="text-gray-400 flex-shrink-0" />
    </a>
  )
}

function RoomInviteBlock({ block }: { block: MessageBlock }) {
  const { roomsById } = useRooms()
  const { lifecycle, failureReason, joinRoom } = useInviteJoin()
  const [copied, setCopied] = useState(false)

  const roomInvite = block.roomInvite
  if (!roomInvite) {
    return null
  }

  const isJoined = Boolean(roomsById[roomInvite.roomId])
  const inviteLink = `${window.location.origin}/chat?join=${roomInvite.roomId}`
  const roomCode = roomInvite.roomId

  const joining = lifecycle === "joining"
  const joinUnavailable = lifecycle === "failed" && failureReason === "invalid"
  const joinError =
    lifecycle === "failed"
      ? failureReason === "invalid"
        ? "This invite is no longer valid."
        : failureReason === "already-member"
        ? "You are already a member."
        : "Unable to join — please try again."
      : null

  const handleCopyInviteLink = async () => {
    if (!navigator.clipboard) {
      return
    }

    await navigator.clipboard.writeText(inviteLink)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  const handleJoin = () => {
    if (joining || isJoined) return
    void joinRoom(roomInvite.roomId)
  }

  return (
    <div className="max-w-sm rounded-xl border border-blue-200 bg-blue-50/60 p-3">
      <div className="text-xs font-semibold uppercase tracking-wide text-blue-600">Group Invite</div>
      <div className="mt-1 text-sm font-medium text-gray-900">
        {roomInvite.roomName?.trim() || "Unnamed room"}
      </div>
      <div className="mt-1 text-xs text-gray-600">
        {roomInvite.memberCount != null ? `${roomInvite.memberCount} members` : "Use link or code to join"}
      </div>
      <div className="mt-2 rounded-lg border border-blue-100 bg-white/80 p-2 text-xs text-gray-600">
        <div className="flex items-center gap-1 text-blue-700">
          <Link2 size={12} />
          <span className="truncate">{inviteLink}</span>
        </div>
        <div className="mt-1 font-mono text-[11px] text-gray-500">Code: {roomCode}</div>
      </div>
      <div className="mt-2 flex items-center gap-2">
        <button
          type="button"
          onClick={() => void handleCopyInviteLink()}
          className="rounded-lg border border-blue-200 bg-white px-2.5 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-50"
          aria-label="Copy invite link"
        >
          <span className="inline-flex items-center gap-1">
            {copied ? <Check size={12} /> : <Copy size={12} />}
            {copied ? "Copied" : "Copy link"}
          </span>
        </button>
        <button
          type="button"
          onClick={handleJoin}
          disabled={joining || isJoined || joinUnavailable}
          className="rounded-lg border border-blue-300 bg-white px-2.5 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isJoined ? "Joined" : joinUnavailable ? "Unavailable" : joining ? "Joining..." : "Join Group"}
        </button>
        {joinError && <span className="text-xs text-red-600">{joinError}</span>}
      </div>
    </div>
  )
}

export default function MessageBlocks({
  blocks,
  resolveMentionLabel,
  resolveMentionUserId,
}: MessageBlocksProps) {
  const renderableBlocks = getRenderableBlocks(blocks)

  if (renderableBlocks.length === 0) {
    return null
  }

  return (
    <div className="flex flex-col gap-1.5 mt-0.5">
      {renderableBlocks.map((block, index) => {
        if (block.type === "TEXT") {
          return (
            <div
              key={`text-${index}`}
              className="text-sm text-gray-800 whitespace-pre-wrap break-words"
            >
              {renderTextWithLinks(
                block.text ?? "",
                resolveMentionLabel,
                resolveMentionUserId
              )}
            </div>
          )
        }

        if (block.type === "ROOM_INVITE") {
          return (
            <div key={`invite-${index}`}>
              <RoomInviteBlock block={block} />
            </div>
          )
        }

        if (!block.attachment) {
          return null
        }

        return (
          <div key={`asset-${index}`}>
            <AssetBlock attachment={block.attachment} />
          </div>
        )
      })}
    </div>
  )
}