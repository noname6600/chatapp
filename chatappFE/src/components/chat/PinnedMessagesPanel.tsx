import { X, Pin } from "lucide-react"
import type { PinnedMessage } from "../../types/message"

interface PinnedMessagesPanelProps {
  open: boolean
  pinnedMessages: PinnedMessage[]
  onClose: () => void
  onJumpToMessage: (messageId: string) => void
  onRequestUnpin: (message: PinnedMessage) => void
}

export default function PinnedMessagesPanel({
  open,
  pinnedMessages,
  onClose,
  onJumpToMessage,
  onRequestUnpin,
}: PinnedMessagesPanelProps) {
  if (!open) return null

  return (
    <div className="absolute top-14 right-4 w-[340px] max-w-[calc(100%-2rem)] rounded-xl border border-gray-200 bg-white shadow-xl z-40 overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
          <Pin size={15} className="text-blue-600" />
          Pinned messages ({pinnedMessages.length})
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded p-1.5 hover:bg-gray-100 text-gray-500"
          aria-label="Close pinned messages"
        >
          <X size={16} />
        </button>
      </div>

      <div className="max-h-96 overflow-y-auto">
        {pinnedMessages.length === 0 ? (
          <div className="px-4 py-8 text-sm text-gray-500 text-center">No pinned messages yet.</div>
        ) : (
          <ul className="divide-y divide-gray-100">
            {pinnedMessages.map((message) => (
              <li key={message.messageId} className="px-3 py-2">
                <div className="flex items-start gap-2">
                  <button
                    type="button"
                    className="flex-1 min-w-0 rounded-md px-2 py-1.5 text-left hover:bg-gray-50"
                    onClick={() => onJumpToMessage(message.messageId)}
                    title="Jump to pinned message"
                  >
                    <div className="text-[11px] text-gray-500 mb-0.5 truncate">
                      {message.createdAt ? new Date(message.createdAt).toLocaleString() : "Pinned message"}
                    </div>
                    <div className="text-sm text-gray-800 truncate">
                      {message.content?.trim() || "(attachment or structured content)"}
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => onRequestUnpin(message)}
                    className="rounded p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50"
                    title="Remove pin"
                    aria-label="Remove pin"
                  >
                    <X size={14} />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
