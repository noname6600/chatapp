import { v4 as uuidv4 } from 'uuid'
import type { DraftState, TextBlock, MediaBlock } from '../types/draft'

/**
 * Legacy draft format: { text: string, attachments: File[] }
 * New format: { roomId: string, blocks: Array<TextBlock | MediaBlock> }
 */
interface LegacyDraft {
  text?: string
  attachments?: File[]
}

/**
 * Migrate legacy draft format to new blocks-based format
 *
 * @param roomId - The room ID this draft belongs to
 * @param legacyDraft - The old draft format
 * @returns New DraftState with blocks array
 */
export function migrateLegacyDraft(
  roomId: string,
  legacyDraft: LegacyDraft | null | undefined
): DraftState {
  const blocks: (TextBlock | MediaBlock)[] = []

  // Add text block if text exists
  if (legacyDraft?.text && legacyDraft.text.trim()) {
    blocks.push({
      id: uuidv4(),
      type: 'text',
      content: legacyDraft.text,
    })
  }

  // Add media blocks if attachments exist
  if (legacyDraft?.attachments && legacyDraft.attachments.length > 0) {
    legacyDraft.attachments.forEach((file) => {
      blocks.push({
        id: uuidv4(),
        type: 'media',
        file,
      })
    })
  }

  return {
    roomId,
    blocks,
  }
}

/**
 * Check if a draft is in legacy format
 */
export function isLegacyDraft(draft: any): draft is LegacyDraft {
  if (!draft) return false
  return typeof draft.text === 'string' || Array.isArray(draft.attachments)
}

/**
 * Safely migrate a draft, handling both new and legacy formats
 */
export function migrateDraftIfNeeded(
  roomId: string,
  draft: any
): DraftState {
  if (!draft) {
    return { roomId, blocks: [] }
  }

  // If already in new format, return as-is
  if (draft.blocks && Array.isArray(draft.blocks) && draft.roomId === roomId) {
    return draft as DraftState
  }

  // Otherwise migrate from legacy format
  return migrateLegacyDraft(roomId, draft)
}
