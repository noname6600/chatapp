import { create } from 'zustand'
import { v4 as uuidv4 } from 'uuid'
import { migrateDraftIfNeeded } from '../utils/draftMigration'
import type {
  DraftBlock,
  DraftState,
  TextBlock,
  MediaBlock,
  DraftByRoom,
} from '../types/draft'
import { isTextBlock } from '../types/draft'

interface DraftStore {
  drafts: DraftByRoom
  currentRoomId: string | null

  // Core actions
  setCurrentRoom: (roomId: string) => void
  initializeDraftWithMigration: (roomId: string, legacyData?: any) => void
  addTextBlock: (roomId: string, content?: string) => void
  addMediaBlock: (roomId: string, file: File, preview?: string) => void
  removeBlock: (roomId: string, blockId: string) => void
  startEditBlock: (roomId: string, blockId: string) => void
  updateEditContent: (roomId: string, content: string) => void
  saveBlockEdit: (roomId: string) => void
  cancelEdit: (roomId: string) => void
  clearDraft: (roomId: string) => void
  getDraft: (roomId: string) => DraftState | undefined
  getDraftBlocks: (roomId: string) => DraftBlock[]
}

export const useDraftStore = create<DraftStore>((set, get) => ({
  drafts: {},
  currentRoomId: null,

  setCurrentRoom: (roomId: string) => {
    set({ currentRoomId: roomId })
    // Initialize empty draft for this room if not exists
    set((state) => {
      if (!state.drafts[roomId]) {
        return {
          drafts: {
            ...state.drafts,
            [roomId]: {
              roomId,
              blocks: [],
            },
          },
        }
      }
      return state
    })
  },

  initializeDraftWithMigration: (roomId: string, legacyData?: any) => {
    set((state) => {
      const migratedDraft = migrateDraftIfNeeded(roomId, legacyData)
      return {
        drafts: {
          ...state.drafts,
          [roomId]: migratedDraft,
        },
      }
    })
  },

  addTextBlock: (roomId: string, content = '') => {
    set((state) => {
      const draft = state.drafts[roomId]
      if (!draft) return state

      const newBlock: TextBlock = {
        id: uuidv4(),
        type: 'text',
        content,
      }

      return {
        drafts: {
          ...state.drafts,
          [roomId]: {
            ...draft,
            blocks: [...draft.blocks, newBlock],
          },
        },
      }
    })
  },

  addMediaBlock: (roomId: string, file: File, preview?: string) => {
    set((state) => {
      const draft = state.drafts[roomId]
      if (!draft) return state

      const newBlock: MediaBlock = {
        id: uuidv4(),
        type: 'media',
        file,
        preview,
      }

      return {
        drafts: {
          ...state.drafts,
          [roomId]: {
            ...draft,
            blocks: [...draft.blocks, newBlock],
          },
        },
      }
    })
  },

  removeBlock: (roomId: string, blockId: string) => {
    set((state) => {
      const draft = state.drafts[roomId]
      if (!draft) return state

      return {
        drafts: {
          ...state.drafts,
          [roomId]: {
            ...draft,
            blocks: draft.blocks.filter((b) => b.id !== blockId),
            // Clear edit state if we removed the edited block
            editingBlockId:
              draft.editingBlockId === blockId ? undefined : draft.editingBlockId,
            editingContent:
              draft.editingBlockId === blockId ? undefined : draft.editingContent,
          },
        },
      }
    })
  },

  startEditBlock: (roomId: string, blockId: string) => {
    set((state) => {
      const draft = state.drafts[roomId]
      if (!draft) return state

      // Save current edit if any before starting new edit
      let updatedBlocks = draft.blocks
      if (draft.editingBlockId && draft.editingContent !== undefined) {
        updatedBlocks = draft.blocks.map((b) =>
          b.id === draft.editingBlockId && isTextBlock(b)
            ? { ...b, content: draft.editingContent }
            : b
        )
      }

      // Find the text block to edit
      const blockToEdit = updatedBlocks.find((b) => b.id === blockId)
      if (!blockToEdit || blockToEdit.type !== 'text') return state

      return {
        drafts: {
          ...state.drafts,
          [roomId]: {
            ...draft,
            blocks: updatedBlocks,
            editingBlockId: blockId,
            editingContent: blockToEdit.content,
          },
        },
      }
    })
  },

  updateEditContent: (roomId: string, content: string) => {
    set((state) => {
      const draft = state.drafts[roomId]
      if (!draft || !draft.editingBlockId) return state

      return {
        drafts: {
          ...state.drafts,
          [roomId]: {
            ...draft,
            editingContent: content,
          },
        },
      }
    })
  },

  saveBlockEdit: (roomId: string) => {
    set((state) => {
      const draft = state.drafts[roomId]
      if (!draft || !draft.editingBlockId || draft.editingContent === undefined)
        return state

      return {
        drafts: {
          ...state.drafts,
          [roomId]: {
            ...draft,
            blocks: draft.blocks.map((b) =>
              b.id === draft.editingBlockId && isTextBlock(b)
                ? { ...b, content: draft.editingContent! }
                : b
            ),
            editingBlockId: undefined,
            editingContent: undefined,
          },
        },
      }
    })
  },

  cancelEdit: (roomId: string) => {
    set((state) => {
      const draft = state.drafts[roomId]
      if (!draft) return state

      return {
        drafts: {
          ...state.drafts,
          [roomId]: {
            ...draft,
            editingBlockId: undefined,
            editingContent: undefined,
          },
        },
      }
    })
  },

  clearDraft: (roomId: string) => {
    set((state) => {
      const draft = state.drafts[roomId]
      if (!draft) return state

      return {
        drafts: {
          ...state.drafts,
          [roomId]: {
            roomId,
            blocks: [],
            editingBlockId: undefined,
            editingContent: undefined,
          },
        },
      }
    })
  },

  getDraft: (roomId: string) => {
    return get().drafts[roomId]
  },

  getDraftBlocks: (roomId: string) => {
    return get().drafts[roomId]?.blocks ?? []
  },
}))
