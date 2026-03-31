import { X } from "lucide-react";
import type { ChatMessage } from "../../types/message";

interface EditPreviewProps {
  message: ChatMessage;
  senderName?: string;
  onClear: () => void;
}

export default function EditPreview({
  message,
  senderName = "Unknown",
  onClear,
}: EditPreviewProps) {
  const preview = message.content?.substring(0, 50) || "(no content)";

  return (
    <div className="relative px-3 py-2 rounded-lg bg-amber-50 border-l-4 border-amber-500 space-y-1">
      <div className="text-xs font-semibold text-amber-700">
        Editing message from {senderName}
      </div>
      <div className="text-sm text-amber-900 line-clamp-2 pr-7">
        {preview}
        {message.content && message.content.length > 50 && "..."}
      </div>
      <button
        onClick={onClear}
        className="absolute top-2 right-2 p-1 hover:bg-amber-200 rounded transition"
        title="Cancel edit"
        type="button"
      >
        <X size={16} className="text-amber-600" />
      </button>
    </div>
  );
}
