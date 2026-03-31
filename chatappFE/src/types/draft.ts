/**
 * Draft composition types - editable draft content blocks before sending
 */

export interface TextBlock {
  id: string
  type: 'text'
  content: string
}

export interface MediaBlock {
  id: string
  type: 'media'
  file: File
  preview?: string  // local preview URL (blob URL or data URL)
}

export type DraftBlock = TextBlock | MediaBlock

export interface DraftState {
  roomId: string
  blocks: DraftBlock[]
  editingBlockId?: string         // which text block is currently being edited
  editingContent?: string         // temporary content for the editing block
}

export interface DraftByRoom {
  [roomId: string]: DraftState | undefined
}

// Type guards
export function isTextBlock(block: DraftBlock): block is TextBlock {
  return block.type === 'text'
}

export function isMediaBlock(block: DraftBlock): block is MediaBlock {
  return block.type === 'media'
}
