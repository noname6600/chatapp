import React, { useCallback } from 'react'
import { useDraftStore } from '../../../store/draft.store'
import { TextBlockComponent } from './TextBlock'
import { MediaBlockComponent } from './MediaBlock'
import { isTextBlock } from '../../../types/draft'

interface DraftComposerProps {
  roomId: string
  onAddMedia?: (files: File[]) => void
}

/**
 * DraftComposer renders all blocks in a draft and handles user interactions
 * for editing and deleting individual blocks
 */
export const DraftComposer: React.FC<DraftComposerProps> = ({
  roomId,
  onAddMedia,
}) => {
  const { getDraft, addTextBlock } = useDraftStore()
  const draft = getDraft(roomId)
  const blocks = draft?.blocks ?? []
  const editingBlockId = draft?.editingBlockId
  const editingContent = draft?.editingContent

  const handleFileInput = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files || [])
      if (files.length > 0 && onAddMedia) {
        onAddMedia(files)
      }
      // Reset input so same file can be selected again
      e.target.value = ''
    },
    [onAddMedia]
  )

  const handleAddTextBlock = () => {
    addTextBlock(roomId)
  }

  if (blocks.length === 0) {
    return (
      <div className="p-4 text-center bg-gray-50 rounded border border-gray-200">
        <p className="text-gray-600 text-sm">No content yet. Click below to start composing.</p>
      </div>
    )
  }

  return (
    <div className="space-y-3 p-4 bg-white rounded border border-gray-200">
      {/* Render all blocks */}
      {blocks.map((block) => (
        <div key={block.id} className="flex gap-2">
          <div className="flex-1">
            {isTextBlock(block) ? (
              <TextBlockComponent
                block={block}
                roomId={roomId}
                isEditing={editingBlockId === block.id}
                editContent={editingBlockId === block.id ? editingContent : undefined}
              />
            ) : (
              <MediaBlockComponent block={block} roomId={roomId} />
            )}
          </div>
        </div>
      ))}

      {/* Action buttons */}
      <div className="flex gap-2 pt-2 border-t border-gray-200">
        <button
          onClick={handleAddTextBlock}
          className="px-3 py-1 text-sm bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
        >
          + Text
        </button>

        <label className="px-3 py-1 text-sm bg-green-500 text-white rounded hover:bg-green-600 transition-colors cursor-pointer">
          + Media
          <input
            type="file"
            multiple
            onChange={handleFileInput}
            className="hidden"
            accept="image/*,video/*,.pdf,.doc,.docx"
          />
        </label>
      </div>
    </div>
  )
}
