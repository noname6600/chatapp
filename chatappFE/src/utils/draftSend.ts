import { DraftBlock, isTextBlock, isMediaBlock } from '../types/draft'

/**
 * Reassemble draft blocks into message format for sending
 * - Concatenates text blocks with newlines
 * - Collects media files as attachments
 */
export function reassembleBlocksForSend(blocks: DraftBlock[]): {
  text: string
  attachments: File[]
} {
  const texts: string[] = []
  const attachments: File[] = []

  for (const block of blocks) {
    if (isTextBlock(block)) {
      const trimmed = block.content.trim()
      if (trimmed) {
        texts.push(trimmed)
      }
    } else if (isMediaBlock(block)) {
      attachments.push(block.file)
    }
  }

  // Join text blocks with newlines
  const text = texts.join('\n')

  return { text, attachments }
}

/**
 * Check if draft has any content to send
 */
export function hasDraftContent(blocks: DraftBlock[]): boolean {
  return blocks.some((block) => {
    if (isTextBlock(block)) {
      return block.content.trim().length > 0
    }
    return isMediaBlock(block)
  })
}

/**
 * Validate draft before sending
 * Returns validation errors if any
 */
export function validateDraftForSend(blocks: DraftBlock[]): string[] {
  const errors: string[] = []

  if (blocks.length === 0) {
    errors.push('Draft is empty')
    return errors
  }

  if (!hasDraftContent(blocks)) {
    errors.push('Draft has no content')
  }

  return errors
}
