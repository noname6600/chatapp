import { describe, it, expect } from 'vitest'
import {
  reassembleBlocksForSend,
  hasDraftContent,
  validateDraftForSend,
} from './draftSend'
import type { DraftBlock } from '../types/draft'

describe('Draft Send - Block Reassembly', () => {
  describe('reassembleBlocksForSend', () => {
    it('should convert text blocks to concatenated text', () => {
      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: 'Hello' },
        { id: '2', type: 'text', content: 'World' },
      ]

      const { text, attachments } = reassembleBlocksForSend(blocks)

      expect(text).toBe('Hello\nWorld')
      expect(attachments).toHaveLength(0)
    })

    it('should collect media files as attachments', () => {
      const file1 = new File(['img1'], 'image1.png', { type: 'image/png' })
      const file2 = new File(['img2'], 'image2.jpg', { type: 'image/jpeg' })

      const blocks: DraftBlock[] = [
        { id: '1', type: 'media', file: file1 },
        { id: '2', type: 'media', file: file2 },
      ]

      const { text, attachments } = reassembleBlocksForSend(blocks)

      expect(text).toBe('')
      expect(attachments).toHaveLength(2)
      expect(attachments[0]).toBe(file1)
      expect(attachments[1]).toBe(file2)
    })

    it('should handle mixed text and media blocks', () => {
      const file = new File(['img'], 'test.png', { type: 'image/png' })

      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: 'Check this image' },
        { id: '2', type: 'media', file },
        { id: '3', type: 'text', content: 'Pretty cool right?' },
      ]

      const { text, attachments } = reassembleBlocksForSend(blocks)

      expect(text).toBe('Check this image\nPretty cool right?')
      expect(attachments).toHaveLength(1)
      expect(attachments[0]).toBe(file)
    })

    it('should skip empty text blocks', () => {
      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: 'Start' },
        { id: '2', type: 'text', content: '   ' }, // Whitespace only
        { id: '3', type: 'text', content: 'End' },
      ]

      const { text } = reassembleBlocksForSend(blocks)

      expect(text).toBe('Start\nEnd')
    })

    it('should trim each text block', () => {
      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: '  Trimmed  ' },
      ]

      const { text } = reassembleBlocksForSend(blocks)

      expect(text).toBe('Trimmed')
    })

    it('should handle text-only drafts', () => {
      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: 'Only text' },
      ]

      const { text, attachments } = reassembleBlocksForSend(blocks)

      expect(text).toBe('Only text')
      expect(attachments).toHaveLength(0)
    })

    it('should handle media-only drafts', () => {
      const file = new File(['img'], 'test.png', { type: 'image/png' })

      const blocks: DraftBlock[] = [
        { id: '1', type: 'media', file },
      ]

      const { text, attachments } = reassembleBlocksForSend(blocks)

      expect(text).toBe('')
      expect(attachments).toHaveLength(1)
    })
  })

  describe('hasDraftContent', () => {
    it('should return true for text blocks with content', () => {
      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: 'Hello' },
      ]

      expect(hasDraftContent(blocks)).toBe(true)
    })

    it('should return true for media blocks', () => {
      const file = new File(['img'], 'test.png', { type: 'image/png' })
      const blocks: DraftBlock[] = [
        { id: '1', type: 'media', file },
      ]

      expect(hasDraftContent(blocks)).toBe(true)
    })

    it('should return false for empty text blocks', () => {
      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: '' },
        { id: '2', type: 'text', content: '   ' },
      ]

      expect(hasDraftContent(blocks)).toBe(false)
    })

    it('should return false for empty blocks array', () => {
      expect(hasDraftContent([])).toBe(false)
    })
  })

  describe('validateDraftForSend', () => {
    it('should return error for empty draft', () => {
      const errors = validateDraftForSend([])

      expect(errors).toContain('Draft is empty')
    })

    it('should return error for draft with no content', () => {
      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: '' },
      ]

      const errors = validateDraftForSend(blocks)

      expect(errors).toContain('Draft has no content')
    })

    it('should return no errors for valid draft', () => {
      const blocks: DraftBlock[] = [
        { id: '1', type: 'text', content: 'Valid content' },
      ]

      const errors = validateDraftForSend(blocks)

      expect(errors).toHaveLength(0)
    })

    it('should return no errors for media-only draft', () => {
      const file = new File(['img'], 'test.png', { type: 'image/png' })
      const blocks: DraftBlock[] = [
        { id: '1', type: 'media', file },
      ]

      const errors = validateDraftForSend(blocks)

      expect(errors).toHaveLength(0)
    })
  })
})
