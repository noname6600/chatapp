import { useState } from "react";
import type { Attachment } from "../types/message";
import { splitSupportedFiles } from "../components/chat/messageInput.behavior";
import { uploadChatAttachment } from "../api/upload.service";

interface UseFileUploadOptions {
  maxFiles?: number;
  maxSize?: number;
}

export function useFileUpload(options: UseFileUploadOptions = {}) {
  const { maxFiles = 5, maxSize = 10 * 1024 * 1024 } = options;
  const [files, setFiles] = useState<File[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const addFiles = (newFiles: File[]) => {
    setError(null);

    const { accepted, rejected } = splitSupportedFiles(newFiles);
    if (rejected.length > 0) {
      const names = rejected.map((file) => file.name).join(", ");
      setError(`Unsupported file type: ${names}`);
      return;
    }

    // Validate count
    if (files.length + accepted.length > maxFiles) {
      setError(`Maximum ${maxFiles} files allowed`);
      return;
    }

    // Validate size
    for (const file of accepted) {
      if (file.size > maxSize) {
        setError(
          `File "${file.name}" exceeds ${(maxSize / 1024 / 1024).toFixed(1)}MB limit`
        );
        return;
      }
    }

    setFiles((prev) => [...prev, ...accepted]);
  };

  const removeFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const clearFiles = () => {
    setFiles([]);
    setError(null);
  };

  const uploadFiles = async () => {
    if (files.length === 0) {
      return null;
    }

    setLoading(true);
    try {
      const failedNames: string[] = [];
      const attachments: Attachment[] = [];

      for (const file of files) {
        try {
          attachments.push(await uploadChatAttachment(file));
        } catch {
          failedNames.push(file.name);
        }
      }

      if (failedNames.length > 0) {
        setError(`Failed to upload: ${failedNames.join(", ")}`);
        return null;
      }

      clearFiles();
      return attachments;
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to upload files"
      );
      return null;
    } finally {
      setLoading(false);
    }
  };

  return {
    files,
    loading,
    error,
    addFiles,
    removeFile,
    clearFiles,
    uploadFiles,
  };
}
