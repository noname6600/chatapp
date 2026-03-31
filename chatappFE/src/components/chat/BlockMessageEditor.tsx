import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react'
import { Trash2, Plus } from 'lucide-react'
import type { MessageBlock } from '../../types/message'
import MentionAutocomplete from './MentionAutocomplete'
import type { MentionSuggestion } from './MentionAutocomplete'
import { useMention } from '../../hooks/useMention'
import { buildMentionToken } from './mention.helpers'

interface BlockMessageEditorProps {
  blocks: MessageBlock[]
  onSave: (blocks: MessageBlock[], content: string) => void
  onCancel: () => void
  isSaving: boolean
  error?: string | null
  currentUserId?: string | null
  candidateUserIds?: string[]
}

/**
 * BlockMessageEditor - Editor for mixed messages with interleaved text/media blocks
 * Allows editing text blocks and removing/adding them while preserving structure
 */
export const BlockMessageEditor: React.FC<BlockMessageEditorProps> = ({
  blocks,
  onSave,
  onCancel,
  isSaving,
  error,
  currentUserId = null,
  candidateUserIds = [],
}) => {
  const [editingBlocks, setEditingBlocks] = useState<MessageBlock[]>(() => {
    // Deep copy blocks to avoid mutating original
    return blocks.map(block => ({ ...block, attachment: block.attachment ? { ...block.attachment } : block.attachment }))
  })
  const [editingBlockIndex, setEditingBlockIndex] = useState<number | null>(null)
  const [editingText, setEditingText] = useState<string>('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const {
    isOpen: mentionOpen,
    suggestions: mentionSuggestions,
    selectedIndex: mentionSelectedIndex,
    detectMention,
    selectMention,
    handleKeyDown: handleMentionKeyDown,
    setIsOpen: setMentionOpen,
  } = useMention({ maxSuggestions: 5, currentUserId, candidateUserIds })

  useEffect(() => {
    if (editingBlockIndex === null || !textareaRef.current) return

    const textarea = textareaRef.current
    textarea.focus()
    textarea.selectionStart = textarea.value.length
    textarea.selectionEnd = textarea.value.length
  }, [editingBlockIndex])

  // Count blocks for UI feedback
  const blockStats = useMemo(() => ({
    textCount: editingBlocks.filter(b => b.type === 'TEXT').length,
    mediaCount: editingBlocks.filter(b => b.type === 'ASSET').length,
    total: editingBlocks.length
  }), [editingBlocks])

  const startEditBlock = useCallback((index: number) => {
    const block = editingBlocks[index]
    if (block.type === 'TEXT') {
      setEditingBlockIndex(index)
      setEditingText(block.text || '')
    }
  }, [editingBlocks])

  const saveBlockEdit = useCallback((index: number) => {
    const newBlocks = [...editingBlocks]
    const block = newBlocks[index]
    if (block.type === 'TEXT') {
      // Update text
      if (editingText.trim()) {
        block.text = editingText.trim()
      } else {
        // Remove empty text blocks
        newBlocks.splice(index, 1)
      }
      setEditingBlocks(newBlocks)
    }
    setEditingBlockIndex(null)
    setEditingText('')
  }, [editingBlocks, editingText])

  const deleteBlock = useCallback((index: number) => {
    const newBlocks = editingBlocks.filter((_, i) => i !== index)
    setEditingBlocks(newBlocks)
    if (editingBlockIndex === index) {
      setEditingBlockIndex(null)
    }
  }, [editingBlocks, editingBlockIndex])

  const addTextBlock = useCallback(() => {
    setEditingBlocks([
      ...editingBlocks,
      { type: 'TEXT', text: '' }
    ])
  }, [editingBlocks])

  const reconstructContent = useCallback(() => {
    return editingBlocks
      .filter(b => b.type === 'TEXT' && b.text)
      .map(b => b.text || '')
      .join(' ')
      .trim()
  }, [editingBlocks])

  const handleSave = useCallback(() => {
    const content = reconstructContent()
    if (!content) return
    
    // Pass blocks as-is to preserve all metadata including attachments
    onSave(editingBlocks, content)
  }, [editingBlocks, reconstructContent, onSave])

  const insertMention = useCallback((suggestion: MentionSuggestion) => {
    const token = buildMentionToken(suggestion)
    if (!token.trim()) return

    const textarea = textareaRef.current
    const cursor = textarea?.selectionStart ?? editingText.length
    const beforeCursor = editingText.substring(0, cursor)
    const atIndex = beforeCursor.lastIndexOf('@')
    if (atIndex === -1) return

    const before = editingText.substring(0, atIndex)
    const after = editingText.substring(cursor).replace(/^\S*/, '')
    const nextValue = `${before}@${token} ${after}`
    const nextCursor = `${before}@${token} `.length

    setEditingText(nextValue)
    selectMention(suggestion)

    requestAnimationFrame(() => {
      if (!textareaRef.current) return
      textareaRef.current.focus()
      textareaRef.current.selectionStart = nextCursor
      textareaRef.current.selectionEnd = nextCursor
      detectMention(nextValue, nextCursor)
    })
  }, [detectMention, editingText, selectMention])

  const handleMentionSelect = useCallback((suggestion: MentionSuggestion) => {
    insertMention(suggestion)
  }, [insertMention])

  return (
    <div className="relative flex flex-col gap-2 p-3 bg-blue-50 border border-blue-300 rounded">
      {/* Header with stats */}
      <div className="text-xs text-blue-700 font-medium">
        Editing: {blockStats.textCount} text, {blockStats.mediaCount} media
      </div>

      {/* Block list */}
      <div className="space-y-2 max-h-64 overflow-y-auto">
        {editingBlocks.length === 0 ? (
          <div className="text-sm text-gray-500 py-2">No content blocks.</div>
        ) : (
          editingBlocks.map((block, idx) => (
            <div key={idx} className="flex gap-2 items-start p-2 bg-white rounded border border-gray-200">
              {block.type === 'TEXT' ? (
                <div className="relative flex-1 flex flex-col gap-1">
                  {editingBlockIndex === idx ? (
                    <>
                      {mentionOpen && mentionSuggestions.length > 0 && (
                        <div className="absolute top-full left-0 right-0 mt-1 z-20">
                          <MentionAutocomplete
                            suggestions={mentionSuggestions}
                            isOpen={mentionOpen}
                            selectedIndex={mentionSelectedIndex}
                            onSelect={handleMentionSelect}
                          />
                        </div>
                      )}
                      <textarea
                        ref={textareaRef}
                        autoFocus
                        value={editingText}
                        onChange={(e) => {
                          setEditingText(e.target.value)
                          detectMention(
                            e.target.value,
                            e.target.selectionStart ?? e.target.value.length
                          )
                        }}
                        onKeyDown={(e) => {
                          if (mentionOpen && mentionSuggestions.length > 0) {
                            const result = handleMentionKeyDown(e)
                            if (result && result !== 'move-up' && result !== 'move-down') {
                              insertMention(result as MentionSuggestion)
                            }
                            return
                          }

                          if (e.key === 'Enter' && e.ctrlKey) {
                            saveBlockEdit(idx)
                          } else if (e.key === 'Escape') {
                            setMentionOpen(false)
                            setEditingBlockIndex(null)
                            setEditingText('')
                          }
                        }}
                        onBlur={() => {
                          if (mentionOpen) return
                          saveBlockEdit(idx)
                        }}
                        className="w-full px-2 py-1 text-sm border border-blue-300 rounded focus:outline-none focus:border-blue-500"
                        rows={2}
                        placeholder="Enter text..."
                      />
                    </>
                  ) : (
                    <button
                      type="button"
                      onClick={() => startEditBlock(idx)}
                      className="text-left text-sm text-gray-800 px-2 py-1 hover:bg-blue-50 rounded text-wrap break-words whitespace-pre-wrap"
                      title="Click to edit"
                    >
                      {block.text || '(empty text block)'}
                    </button>
                  )}
                </div>
              ) : (
                <div className="flex-1">
                  <div className="text-sm text-gray-600 px-2 py-1 bg-gray-50 rounded">
                    <div className="font-semibold">📎 Attachment (Read-only)</div>
                    <div className="text-xs text-gray-500 mt-1">
                      Type: {block.attachment?.type}
                      {block.attachment?.fileName && ` • ${block.attachment.fileName}`}
                      {block.attachment?.url && <div className="truncate text-xs">{block.attachment.url}</div>}
                    </div>
                  </div>
                </div>
              )}
              {block.type === 'TEXT' && (
                <button
                  type="button"
                  onClick={() => deleteBlock(idx)}
                  disabled={isSaving}
                  className="p-1 rounded hover:bg-red-50 transition disabled:opacity-50 flex-shrink-0"
                  title="Remove text block"
                >
                  <Trash2 size={14} className="text-red-500" />
                </button>
              )}
            </div>
          ))
        )}
      </div>

      {/* Action buttons */}
      <div className="flex gap-1.5 items-center border-t border-blue-200 pt-2">
        <button
          type="button"
          onClick={addTextBlock}
          disabled={isSaving}
          className="flex items-center gap-1 px-2 py-1 text-xs bg-blue-100 text-blue-700 rounded hover:bg-blue-200 transition disabled:opacity-50"
        >
          <Plus size={12} />
          Text
        </button>
        <div className="flex-1" />
        <button
          type="button"
          onClick={onCancel}
          disabled={isSaving}
          className="px-2 py-1 text-xs rounded hover:bg-gray-100 transition disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={handleSave}
          disabled={isSaving || reconstructContent().length === 0}
          className="px-2 py-1 text-xs bg-green-500 text-white rounded hover:bg-green-600 transition disabled:opacity-50"
        >
          Save
        </button>
      </div>
      {error && (
        <div className="text-xs text-red-600 px-2">{error}</div>
      )}
    </div>
  )
}
