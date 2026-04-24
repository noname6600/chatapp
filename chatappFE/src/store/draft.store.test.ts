import { describe, it, expect, beforeEach } from 'vitest'
import { useDraftStore } from './draft.store'

describe('Draft Store - Block Operations', () => {
  beforeEach(() => {
    // Reset store state before each test
    const { drafts } = useDraftStore.getState()
    Object.keys(drafts).forEach((roomId) => {
      useDraftStore.getState().clearDraft(roomId)
    })
  })

  describe('addTextBlock', () => {
    it('should add a text block to the draft', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'Hello')

      const draft = useDraftStore.getState().getDraft(roomId)
      expect(draft?.blocks).toHaveLength(1)
      expect(draft?.blocks[0]).toMatchObject({
        type: 'text',
        content: 'Hello',
      })
    })

    it('should add multiple text blocks', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'First')
      useDraftStore.getState().addTextBlock(roomId, 'Second')

      const draft = useDraftStore.getState().getDraft(roomId)
      expect(draft?.blocks).toHaveLength(2)
      expect((draft?.blocks[0] as { content: string }).content).toBe('First')
      expect((draft?.blocks[1] as { content: string }).content).toBe('Second')
    })
  })

  describe('removeBlock', () => {
    it('should remove a block by id', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'Text 1')
      useDraftStore.getState().addTextBlock(roomId, 'Text 2')

      const draft1 = useDraftStore.getState().getDraft(roomId)
      const blockIdToRemove = draft1!.blocks[0].id

      useDraftStore.getState().removeBlock(roomId, blockIdToRemove)

      const draft2 = useDraftStore.getState().getDraft(roomId)
      expect(draft2?.blocks).toHaveLength(1)
      expect((draft2?.blocks[0] as { content: string }).content).toBe('Text 2')
    })

    it('should clear editing state when removing edited block', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'Text')

      const draft1 = useDraftStore.getState().getDraft(roomId)
      const blockId = draft1!.blocks[0].id

      useDraftStore.getState().startEditBlock(roomId, blockId)
      useDraftStore.getState().removeBlock(roomId, blockId)

      const draft2 = useDraftStore.getState().getDraft(roomId)
      expect(draft2?.editingBlockId).toBeUndefined()
    })
  })

  describe('startEditBlock and saveBlockEdit', () => {
    it('should enter edit mode for a text block', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'Original')

      const draft1 = useDraftStore.getState().getDraft(roomId)
      const blockId = draft1!.blocks[0].id

      useDraftStore.getState().startEditBlock(roomId, blockId)

      const draft2 = useDraftStore.getState().getDraft(roomId)
      expect(draft2?.editingBlockId).toBe(blockId)
      expect(draft2?.editingContent).toBe('Original')
    })

    it('should save block edit', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'Original')

      const draft1 = useDraftStore.getState().getDraft(roomId)
      const blockId = draft1!.blocks[0].id

      useDraftStore.getState().startEditBlock(roomId, blockId)
      useDraftStore.getState().updateEditContent(roomId, 'Modified')
      useDraftStore.getState().saveBlockEdit(roomId)

      const draft2 = useDraftStore.getState().getDraft(roomId)
      expect((draft2?.blocks[0] as { content: string }).content).toBe('Modified')
      expect(draft2?.editingBlockId).toBeUndefined()
    })
  })

  describe('cancelEdit', () => {
    it('should cancel edit and discard changes', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'Original')

      const draft1 = useDraftStore.getState().getDraft(roomId)
      const blockId = draft1!.blocks[0].id

      useDraftStore.getState().startEditBlock(roomId, blockId)
      useDraftStore.getState().updateEditContent(roomId, 'Modified')
      useDraftStore.getState().cancelEdit(roomId)

      const draft2 = useDraftStore.getState().getDraft(roomId)
      expect((draft2?.blocks[0] as { content: string }).content).toBe('Original') // Unchanged
      expect(draft2?.editingBlockId).toBeUndefined()
    })
  })

  describe('Edit mode - only one block at a time', () => {
    it('should auto-save previous edit when starting new edit', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'Text 1')
      useDraftStore.getState().addTextBlock(roomId, 'Text 2')

      const draft1 = useDraftStore.getState().getDraft(roomId)
      const block1Id = draft1!.blocks[0].id
      const block2Id = draft1!.blocks[1].id

      // Edit first block
      useDraftStore.getState().startEditBlock(roomId, block1Id)
      useDraftStore.getState().updateEditContent(roomId, 'Text 1 Modified')

      // Start editing second block (should auto-save first)
      useDraftStore.getState().startEditBlock(roomId, block2Id)

      const draft2 = useDraftStore.getState().getDraft(roomId)
      expect((draft2?.blocks[0] as { content: string }).content).toBe('Text 1 Modified') // First saved
      expect(draft2?.editingBlockId).toBe(block2Id) // Second now editing
    })
  })

  describe('clearDraft', () => {
    it('should clear all blocks and edit state', () => {
      const roomId = 'room-1'
      useDraftStore.getState().setCurrentRoom(roomId)
      useDraftStore.getState().addTextBlock(roomId, 'Text')
      useDraftStore.getState().addMediaBlock(
        roomId,
        new File(['content'], 'test.txt')
      )

      useDraftStore.getState().clearDraft(roomId)

      const draft = useDraftStore.getState().getDraft(roomId)
      expect(draft?.blocks).toHaveLength(0)
      expect(draft?.editingBlockId).toBeUndefined()
    })
  })
})
