import { FileText, Image, Video, X } from "lucide-react";
import clsx from "clsx";
import type { Attachment } from "../../types/message";

interface FilePreviewProps {
  files?: (Attachment | File)[];
  onRemove?: (index: number) => void;
  loading?: boolean;
  className?: string;
}

function getFileIcon(file: Attachment | File) {
  const type = file instanceof File ? file.type : file.type;

  if (type.startsWith("image/")) return <Image size={20} />;
  if (type.startsWith("video/")) return <Video size={20} />;
  return <FileText size={20} />;
}

function getFileName(file: Attachment | File) {
  if (file instanceof File) {
    return file.name;
  }
  return file.name || "file";
}

function getFileSize(file: File | Attachment) {
  if (file instanceof File) {
    return (file.size / 1024).toFixed(2);
  }
  const size = (file as any).size || 0;
  return (size / 1024).toFixed(2);
}

export default function FilePreview({
  files = [],
  onRemove,
  loading = false,
  className,
}: FilePreviewProps) {
  if (files.length === 0) return null;

  return (
    <div className={clsx("space-y-2", className)}>
      <div className="text-xs font-semibold text-gray-600">
        Attachments ({files.length})
      </div>
      <div className="flex flex-wrap gap-2">
        {files.map((file, idx) => (
          <div
            key={idx}
            className={clsx(
              "relative group flex items-center gap-2 p-2 rounded-lg",
              "bg-gray-100 border border-gray-300 text-sm",
              "hover:bg-gray-200 transition",
              loading && "opacity-50"
            )}
          >
            <div className="text-gray-600">{getFileIcon(file)}</div>

            <div className="min-w-0">
              <div className="font-medium text-gray-900 truncate">
                {getFileName(file)}
              </div>
              <div className="text-xs text-gray-500">
                {getFileSize(file)} KB
              </div>
            </div>

            {onRemove && !loading && (
              <button
                onClick={() => onRemove(idx)}
                className="ml-2 p-1 opacity-0 group-hover:opacity-100 hover:bg-gray-300 rounded transition"
                title="Remove file"
                type="button"
              >
                <X size={16} />
              </button>
            )}

            {loading && (
              <div className="ml-2 w-4 h-4 border-2 border-gray-400 border-t-blue-500 rounded-full animate-spin" />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
