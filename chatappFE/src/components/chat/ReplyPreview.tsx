import { Image as ImageIcon, Paperclip, X } from "lucide-react";
import clsx from "clsx";
import type { ChatMessage } from "../../types/message";
import UserAvatar from "../user/UserAvatar";
import { buildReplyPreviewModel } from "../../utils/replyPreview";

interface ReplyPreviewProps {
  message: ChatMessage | null;
  senderName?: string;
  senderAvatar?: string | null;
  currentUserId?: string | null;
  isMissingOriginal?: boolean;
  onClear?: () => void;
  className?: string;
}

export default function ReplyPreview({
  message,
  senderName = "Unknown",
  senderAvatar = null,
  currentUserId = null,
  isMissingOriginal = false,
  onClear,
  className,
}: ReplyPreviewProps) {
  const preview = buildReplyPreviewModel({
    message,
    senderName,
    senderAvatar,
    currentUserId,
    isMissingOriginal,
  });

  const isHighlighted = preview.isOwnTarget && !preview.isMissingOriginal;

  return (
    <div
      className={clsx(
        "flex items-start gap-3 p-3 rounded-lg",
        isHighlighted
          ? "bg-amber-50"
          : "bg-blue-50",
        "text-sm",
        className
      )}
    >
      {/* Left accent bar */}
      <div
        className={clsx(
          "w-1 rounded-full flex-shrink-0 mt-1",
          isHighlighted ? "bg-amber-500" : "bg-blue-500"
        )}
      />

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          <UserAvatar userId={message?.senderId ?? "unknown"} avatar={preview.senderAvatar} size={16} />
          <div
            className={clsx(
              "font-semibold text-xs",
              isHighlighted ? "text-amber-900" : "text-blue-900"
            )}
          >
            Replying to {preview.senderName}
          </div>
        </div>

        <div
          className={clsx(
            "text-xs line-clamp-1 mt-0.5 flex items-center gap-1",
            preview.isMissingOriginal
              ? "text-red-700"
              : isHighlighted
                ? "text-amber-800"
                : "text-blue-800"
          )}
        >
          {preview.kind === "media" && <ImageIcon size={12} className="opacity-70" />}
          {preview.kind === "missing" && <Paperclip size={12} className="opacity-70" />}
          <span>{preview.previewText}</span>
        </div>
      </div>

      {onClear && (
        <button
          onClick={onClear}
          className="p-1 hover:bg-blue-100 rounded transition flex-shrink-0"
          title="Clear reply"
          type="button"
        >
          <X size={16} className="text-blue-600" />
        </button>
      )}
    </div>
  );
}
