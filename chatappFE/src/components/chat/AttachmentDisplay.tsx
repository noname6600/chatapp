import { Download, FileText, Image, Video } from "lucide-react";
import type { Attachment } from "../../types/message";

interface AttachmentDisplayProps {
  attachments: Attachment[];
}

function getAttachmentIcon(attachment: Attachment) {
  switch (attachment.type) {
    case "IMAGE":
      return <Image size={40} />;
    case "VIDEO":
      return <Video size={40} />;
    case "FILE":
      return <FileText size={40} />;
    default:
      return <FileText size={40} />;
  }
}

export default function AttachmentDisplay({
  attachments,
}: AttachmentDisplayProps) {
  if (!attachments || attachments.length === 0) return null;

  return (
    <div className="mt-2 space-y-2">
      {/* Image previews */}
      {attachments
        .filter((a) => a.type === "IMAGE")
        .map((att) => (
          <a
            key={att.id || att.url}
            href={att.url}
            target="_blank"
            rel="noopener noreferrer"
            className="block max-w-xs"
          >
            <img
              src={att.url}
              alt={att.name || "Image"}
              className="rounded-lg max-h-72 object-cover hover:opacity-90 transition"
            />
          </a>
        ))}

      {/* Other file list */}
      {attachments
        .filter((a) => a.type !== "IMAGE")
        .map((att) => (
          <a
            key={att.id || att.url}
            href={att.url}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-3 p-2 rounded-lg bg-gray-50 hover:bg-gray-100 transition border border-gray-200 max-w-xs"
          >
            <div className="text-gray-600 flex-shrink-0">
              {getAttachmentIcon(att)}
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium text-gray-900 truncate">
                {att.name || "file"}
              </div>
              <div className="text-xs text-gray-500">
                {att.size ? `${(att.size / 1024).toFixed(2)} KB` : ""}
              </div>
            </div>
            <Download size={16} className="text-gray-400 flex-shrink-0" />
          </a>
        ))}
    </div>
  );
}
