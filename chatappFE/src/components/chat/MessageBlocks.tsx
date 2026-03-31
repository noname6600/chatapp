import { Download, FileText, Image as ImageIcon, Video } from "lucide-react"
import type { ReactNode } from "react"

import type { Attachment, MessageBlock } from "../../types/message"
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