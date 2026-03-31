import type { Attachment, MessageBlock } from "../../types/message"

export type DraftTextBlock = {
  id: string
  type: "TEXT"
  text: string
}

export type DraftAssetBlock = {
  id: string
  type: "ASSET"
  status: "uploading" | "ready" | "failed"
  attachment?: Attachment
  fileName: string
  size?: number
  previewUrl?: string
  mimeType?: string
  error?: string
}

export type DraftBlock = DraftTextBlock | DraftAssetBlock

export function appendTextBlock(blocks: DraftBlock[], text: string): DraftBlock[] {
  const normalized = text.trim()
  if (!normalized) {
    return blocks
  }

  const next = [...blocks]
  const last = next.at(-1)

  if (last && last.type === "TEXT") {
    next[next.length - 1] = {
      ...last,
      text: `${last.text}\n${normalized}`,
    }
    return next
  }

  next.push({
    id: crypto.randomUUID(),
    type: "TEXT",
    text: normalized,
  })
  return next
}

export function buildMessageBlocks(blocks: DraftBlock[], trailingText: string): MessageBlock[] {
  const normalized = appendTextBlock(blocks, trailingText)

  return normalized.reduce<MessageBlock[]>((result, block) => {
    if (block.type === "TEXT") {
      result.push({
        type: "TEXT",
        text: block.text,
      })
      return result
    }

    if (block.status !== "ready" || !block.attachment) {
      return result
    }

    result.push({
      type: "ASSET",
      attachment: block.attachment,
    })
    return result
  }, [])
}

export function getAssetCount(blocks: DraftBlock[]): number {
  return blocks.filter((block) => block.type === "ASSET").length
}

export function hasUploadingBlocks(blocks: DraftBlock[]): boolean {
  return blocks.some((block) => block.type === "ASSET" && block.status === "uploading")
}

export function hasFailedBlocks(blocks: DraftBlock[]): boolean {
  return blocks.some((block) => block.type === "ASSET" && block.status === "failed")
}

export function createUploadingAssetPlaceholders(
  _blocks: DraftBlock[],
  _trailingText: string,
  files: File[]
): DraftAssetBlock[] {
  return files.map((file) => ({
    id: crypto.randomUUID(),
    type: "ASSET",
    status: "uploading",
    fileName: file.name,
    size: file.size,
    mimeType: file.type,
    previewUrl: file.type.startsWith("image/") ? URL.createObjectURL(file) : undefined,
  }))
}

export function revokePreviewUrl(block: DraftBlock) {
  if (block.type === "ASSET" && block.previewUrl) {
    URL.revokeObjectURL(block.previewUrl)
  }
}