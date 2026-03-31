import { useRef } from "react";
import { Upload } from "lucide-react";
import clsx from "clsx";

interface FileUploadProps {
  onFilesSelected?: (files: File[]) => void;
  maxSize?: number; // in bytes
  maxFiles?: number;
  accept?: string;
  disabled?: boolean;
  className?: string;
}

export default function FileUpload({
  onFilesSelected,
  maxSize = 10 * 1024 * 1024, // 10MB
  maxFiles = 5,
  accept = "image/*,video/*,.pdf,.doc,.docx,.txt",
  disabled = false,
  className,
}: FileUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const dragOverRef = useRef(false);

  const handleFiles = (files: FileList) => {
    const fileArray = Array.from(files);

    // Validate
    if (fileArray.length > maxFiles) {
      alert(`Maximum ${maxFiles} files allowed`);
      return;
    }

    for (const file of fileArray) {
      if (file.size > maxSize) {
        alert(
          `File "${file.name}" exceeds ${(maxSize / 1024 / 1024).toFixed(1)}MB limit`
        );
        return;
      }
    }

    onFilesSelected?.(fileArray);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    dragOverRef.current = true;
  };

  const handleDragLeave = () => {
    dragOverRef.current = false;
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    dragOverRef.current = false;
    handleFiles(e.dataTransfer.files);
  };

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={clsx(
        "relative border-2 border-dashed rounded-lg p-4 transition",
        dragOverRef.current
          ? "border-blue-500 bg-blue-50"
          : "border-gray-300 hover:border-gray-400 bg-gray-50",
        disabled && "opacity-50 cursor-not-allowed",
        className
      )}
    >
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={accept}
        onChange={(e) => e.target.files && handleFiles(e.target.files)}
        disabled={disabled}
        className="hidden"
      />

      <button
        onClick={() => inputRef.current?.click()}
        disabled={disabled}
        className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-sm text-gray-600"
        type="button"
      >
        <Upload size={24} />
        <span className="font-medium">Drag files here or click to upload</span>
        <span className="text-xs text-gray-500">
          Max {maxFiles} file{maxFiles > 1 ? "s" : ""}, {(maxSize / 1024 / 1024).toFixed(1)}MB each
        </span>
      </button>
    </div>
  );
}
