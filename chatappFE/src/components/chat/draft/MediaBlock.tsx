import React, { useEffect, useState } from 'react'
import { useDraftStore } from '../../../store/draft.store'
import type { MediaBlock } from '../../../types/draft'

interface MediaBlockProps {
  block: MediaBlock
  roomId: string
}

export const MediaBlockComponent: React.FC<MediaBlockProps> = ({
  block,
  roomId,
}) => {
  const { removeBlock } = useDraftStore()
  const [previewUrl, setPreviewUrl] = useState<string | null>(block.preview || null)

  // Generate preview for images
  useEffect(() => {
    if (!previewUrl && block.file.type.startsWith('image/')) {
      const reader = new FileReader()
      reader.onload = (e) => {
        setPreviewUrl(e.target?.result as string)
      }
      reader.readAsDataURL(block.file)
    }
  }, [block.file, previewUrl])

  // Cleanup blob URLs
  useEffect(() => {
    return () => {
      if (previewUrl && previewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(previewUrl)
      }
    }
  }, [previewUrl])

  const handleRemove = () => {
    removeBlock(roomId, block.id)
  }

  const isImage = block.file.type.startsWith('image/')
  const isVideo = block.file.type.startsWith('video/')

  return (
    <div className="relative inline-block group">
      {isImage && previewUrl && (
        <img
          src={previewUrl}
          alt={block.file.name || 'Media'}
          className="max-w-sm max-h-64 rounded border border-gray-300"
        />
      )}
      {isVideo && (
        <video
          src={previewUrl || undefined}
          controls
          className="max-w-sm max-h-64 rounded border border-gray-300"
        />
      )}
      {!isImage && !isVideo && (
        <div className="flex items-center gap-2 p-2 bg-gray-100 rounded border border-gray-300">
          <div className="text-2xl">📎</div>
          <div className="text-sm">
            <div className="font-medium">{block.file.name || 'File'}</div>
            <div className="text-gray-600">
              {(block.file.size || 0) > 0
                ? `${((block.file.size || 0) / 1024).toFixed(1)} KB`
                : 'Unknown size'}
            </div>
          </div>
        </div>
      )}

      {/* Delete button - visible on hover */}
      <button
        onClick={handleRemove}
        className="absolute -top-2 -right-2 hidden group-hover:flex items-center justify-center w-6 h-6 bg-red-500 text-white rounded-full hover:bg-red-600 transition-colors"
        title="Remove media"
        aria-label="Remove media"
      >
        ✕
      </button>
    </div>
  )
}
