import { Send, RotateCcw, X } from "lucide-react";
import { Button } from "../ui/Button";

interface DraftReviewModalProps {
  isOpen: boolean;
  draftText: string;
  originalText: string;
  attachmentCount: number;
  replyToMessageId?: string | null;
  sending?: boolean;
  onDraftTextChange: (value: string) => void;
  onCancel: () => void;
  onRestoreOriginal: () => void;
  onSend: () => void;
}

export default function DraftReviewModal({
  isOpen,
  draftText,
  originalText,
  attachmentCount,
  replyToMessageId,
  sending = false,
  onDraftTextChange,
  onCancel,
  onRestoreOriginal,
  onSend,
}: DraftReviewModalProps) {
  if (!isOpen) {
    return null;
  }

  const hasChanges = draftText !== originalText;
  const canSend = draftText.trim().length > 0 || attachmentCount > 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-2xl rounded-xl border border-gray-200 bg-white p-4 shadow-xl md:p-5">
        <div className="mb-3 flex items-start justify-between gap-3">
          <div>
            <h2 className="text-base font-semibold text-gray-900">Review draft</h2>
            <p className="text-xs text-gray-500">
              Check and edit your message before sending.
            </p>
          </div>
          <button
            type="button"
            onClick={onCancel}
            className="rounded p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
            aria-label="Close review modal"
          >
            <X size={18} />
          </button>
        </div>

        <textarea
          value={draftText}
          onChange={(event) => onDraftTextChange(event.target.value)}
          className="min-h-36 w-full resize-y rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none focus:border-blue-400"
          placeholder="Write your message..."
          autoFocus
        />

        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500">
          <span>{attachmentCount} attachment{attachmentCount === 1 ? "" : "s"}</span>
          {replyToMessageId ? <span>Reply context kept</span> : null}
          {hasChanges ? <span className="text-blue-600">Edited draft</span> : null}
        </div>

        <div className="mt-4 flex flex-wrap justify-end gap-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onRestoreOriginal}
            disabled={!hasChanges || sending}
            className="gap-1.5"
          >
            <RotateCcw size={14} />
            Restore
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onCancel}
            disabled={sending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={onSend}
            disabled={!canSend || sending}
            className="gap-1.5"
            aria-label="Send reviewed message"
          >
            <Send size={14} />
            {sending ? "Sending..." : "Send"}
          </Button>
        </div>
      </div>
    </div>
  );
}
