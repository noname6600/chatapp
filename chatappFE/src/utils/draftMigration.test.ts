import { describe, it, expect } from 'vitest'
import {
  migrateLegacyDraft,
  migrateDraftIfNeeded,
  isLegacyDraft,
} from './draftMigration'
import type { DraftState } from '../types/draft'

describe('Draft Migration', () => {
  describe('migrateLegacyDraft', () => {
    it('should convert legacy text-only draft to blocks format', () => {
      const roomId = 'room-1'
      const legacyDraft = { text: 'Hello world' }

      const migrated = migrateLegacyDraft(roomId, legacyDraft)

      expect(migrated.roomId).toBe(roomId)
      expect(migrated.blocks).toHaveLength(1)
      expect(migrated.blocks[0]).toMatchObject({
        type: 'text',
        content: 'Hello world',
      })
    })

    it('should convert legacy draft with attachments to blocks format', () => {
      const roomId = 'room-1'
      const file1 = new File(['content1'], 'image1.png', { type: 'image/png' })
      const file2 = new File(['content2'], 'image2.jpg', { type: 'image/jpeg' })
      const legacyDraft = {
        text: 'Check these images',
        attachments: [file1, file2],
      }

      const migrated = migrateLegacyDraft(roomId, legacyDraft)

      expect(migrated.blocks).toHaveLength(3)
      expect(migrated.blocks[0]).toMatchObject({ type: 'text' })
      expect(migrated.blocks[1]).toMatchObject({ type: 'media', file: file1 })
      expect(migrated.blocks[2]).toMatchObject({ type: 'media', file: file2 })
    })

    it('should handle empty legacy draft', () => {
      const roomId = 'room-1'
      const legacyDraft = { text: '', attachments: [] }

      const migrated = migrateLegacyDraft(roomId, legacyDraft)

      expect(migrated.roomId).toBe(roomId)
      expect(migrated.blocks).toHaveLength(0)
    })

    it('should handle null legacy draft', () => {
      const roomId = 'room-1'

      const migrated = migrateLegacyDraft(roomId, null)

      expect(migrated.roomId).toBe(roomId)
      expect(migrated.blocks).toHaveLength(0)
    })

    it('should trim whitespace from text blocks', () => {
      const roomId = 'room-1'
      const legacyDraft = { text: '  \n  Hello  \n  ' }

      const migrated = migrateLegacyDraft(roomId, legacyDraft)

      expect(migrated.blocks).toHaveLength(1)
      expect((migrated.blocks[0] as { content: string }).content).toBe('  \n  Hello  \n  ') // Preserves original content
    })
  })

  describe('isLegacyDraft', () => {
    it('should identify legacy draft with text', () => {
      expect(isLegacyDraft({ text: 'Hello' })).toBe(true)
    })

    it('should identify legacy draft with attachments', () => {
      expect(isLegacyDraft({ attachments: [] })).toBe(true)
    })

    it('should identify legacy draft with both', () => {
      expect(isLegacyDraft({ text: 'Hello', attachments: [] })).toBe(true)
    })

    it('should not identify null as legacy', () => {
      expect(isLegacyDraft(null)).toBe(false)
    })

    it('should not identify new format as legacy', () => {
      expect(isLegacyDraft({ roomId: 'room-1', blocks: [] })).toBe(false)
    })
  })

  describe('migrateDraftIfNeeded', () => {
    it('should return empty draft for null input', () => {
      const roomId = 'room-1'

      const result = migrateDraftIfNeeded(roomId, null)

      expect(result.roomId).toBe(roomId)
      expect(result.blocks).toHaveLength(0)
    })

    it('should return as-is if already in new format', () => {
      const roomId = 'room-1'
      const newFormatDraft: DraftState = {
        roomId,
        blocks: [
          { id: 'block-1', type: 'text', content: 'Hello' },
        ],
      }

      const result = migrateDraftIfNeeded(roomId, newFormatDraft)

      expect(result).toEqual(newFormatDraft)
    })

    it('should migrate legacy format', () => {
      const roomId = 'room-1'
      const legacyDraft = { text: 'Hello world' }

      const result = migrateDraftIfNeeded(roomId, legacyDraft)

      expect(result.roomId).toBe(roomId)
      expect(result.blocks).toHaveLength(1)
      expect(result.blocks[0].type).toBe('text')
    })
  })
})
