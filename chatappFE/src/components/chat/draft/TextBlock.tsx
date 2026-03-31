import React, { useRef, useEffect } from 'react'
import { useDraftStore } from '../../../store/draft.store'
import type { TextBlock } from '../../../types/draft'

interface TextBlockProps {
  block: TextBlock
  roomId: string
  isEditing: boolean
  editContent?: string
}

export const TextBlockComponent: React.FC<TextBlockProps> = ({
  block,
  roomId,
  isEditing,
  editContent,
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const { startEditBlock, updateEditContent, saveBlockEdit, cancelEdit } =
    useDraftStore()

  // Focus textarea when entering edit mode
  useEffect(() => {
    if (isEditing && textareaRef.current) {
      textareaRef.current.focus()
      textareaRef.current.select()
    }
  }, [isEditing])

  const handleClick = () => {
    if (!isEditing) {
      startEditBlock(roomId, block.id)
    }
  }

  const handleTextareaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    updateEditContent(roomId, e.target.value)
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      cancelEdit(roomId)
    } else if (e.key === 'Enter' && e.ctrlKey) {
      e.preventDefault()
      saveBlockEdit(roomId)
    }
  }

  const handleBlur = () => {
    saveBlockEdit(roomId)
  }

  if (isEditing) {
    return (
      <textarea
        ref={textareaRef}
        value={editContent || ''}
        onChange={handleTextareaChange}
        onKeyDown={handleKeyDown}
        onBlur={handleBlur}
        className="w-full p-2 border border-blue-400 rounded bg-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
        rows={Math.max(3, (editContent || '').split('\n').length)}
      />
    )
  }

  return (
    <div
      onClick={handleClick}
      className="p-2 rounded cursor-text hover:bg-gray-50 whitespace-pre-wrap break-words text-sm"
    >
      {block.content || <span className="text-gray-400">Click to add text...</span>}
    </div>
  )
}
