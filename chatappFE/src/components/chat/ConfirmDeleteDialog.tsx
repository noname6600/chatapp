import { AlertTriangle } from "lucide-react";
import { Button } from "../ui/Button";

interface ConfirmDeleteDialogProps {
  messageId: string;
  messagePreview: string;
  onConfirm: (messageId: string) => void;
  onCancel: () => void;
  loading?: boolean;
}

export default function ConfirmDeleteDialog({
  messageId,
  messagePreview,
  onConfirm,
  onCancel,
  loading = false,
}: ConfirmDeleteDialogProps) {
  const preview = messagePreview.substring(0, 60) || "(no content)";

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full space-y-4">
        <div className="flex items-center gap-3">
          <AlertTriangle size={24} className="text-red-500" />
          <h2 className="text-lg font-semibold text-gray-900">Delete message?</h2>
        </div>

        <p className="text-sm text-gray-600">
          Are you sure you want to delete this message? This action cannot be undone.
        </p>

        <div className="p-3 rounded-lg bg-gray-50 border border-gray-200">
          <p className="text-sm text-gray-700 truncate">
            {preview}
            {messagePreview.length > 60 && "..."}
          </p>
        </div>

        <div className="flex gap-3 justify-end">
          <Button
            variant="outline"
            onClick={onCancel}
            disabled={loading}
            size="sm"
          >
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={() => onConfirm(messageId)}
            disabled={loading}
            size="sm"
            className="gap-2"
          >
            {loading ? "Deleting..." : "Delete"}
          </Button>
        </div>
      </div>
    </div>
  );
}
