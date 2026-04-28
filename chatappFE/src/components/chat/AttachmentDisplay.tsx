import { Download, FileText, Image, Video } from "lucide-react";
import { useState } from "react";
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
  const [previewImageUrl, setPreviewImageUrl] = useState<string | null>(null);
  const [confirmUrl, setConfirmUrl] = useState<string | null>(null);

  if (!attachments || attachments.length === 0) return null;

  const openExternal = () => {
    if (!confirmUrl) return;
    window.open(confirmUrl, "_blank", "noopener,noreferrer");
    setConfirmUrl(null);
  };

  return (
    <>
      <div className="mt-2 space-y-2">
        {/* Image previews */}
        {attachments
          .filter((a) => a.type === "IMAGE")
          .map((att) => (
            <button
              key={att.id || att.url}
              type="button"
              onClick={() => setPreviewImageUrl(att.url)}
              className="block max-w-xs"
            >
              <img
                src={att.url}
                alt={att.name || "Image"}
                className="rounded-lg max-h-72 object-cover hover:opacity-90 transition"
              />
            </button>
          ))}

        {/* Other file list */}
        {attachments
          .filter((a) => a.type !== "IMAGE")
          .map((att) => (
            <button
              key={att.id || att.url}
              type="button"
              onClick={() => setConfirmUrl(att.url)}
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
            </button>
          ))}
      </div>

      {previewImageUrl && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
          <div className="w-full max-w-xl rounded-xl bg-white p-4 shadow-lg">
            <img src={previewImageUrl} alt="Preview" className="max-h-[70vh] w-full rounded-lg object-contain" />
            <div className="mt-3 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setPreviewImageUrl(null)}
                className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-100"
              >
                Close
              </button>
              <button
                type="button"
                onClick={() => {
                  window.open(previewImageUrl, "_blank", "noopener,noreferrer");
                  setPreviewImageUrl(null);
                }}
                className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-blue-700"
              >
                View Full
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmUrl && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4">
          <div className="w-full max-w-md rounded-xl bg-white p-4 shadow-lg">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-amber-700">Open Link</h3>
            <p className="mt-2 text-sm text-gray-600 break-all">Do you want to open this link in a new tab?</p>
            <p className="mt-1 text-xs text-gray-500 break-all">{confirmUrl}</p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setConfirmUrl(null)}
                className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-100"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={openExternal}
                className="rounded-md bg-amber-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-amber-700"
              >
                Open Link
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
