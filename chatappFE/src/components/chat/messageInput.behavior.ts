const ALLOWED_MIME_PREFIXES = ["image/", "video/"];

const ALLOWED_MIME_TYPES = new Set([
  "application/pdf",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "text/plain",
]);

const ALLOWED_EXTENSIONS = new Set([
  ".png",
  ".jpg",
  ".jpeg",
  ".gif",
  ".webp",
  ".svg",
  ".mp4",
  ".mov",
  ".avi",
  ".mkv",
  ".pdf",
  ".doc",
  ".docx",
  ".txt",
]);

export type MessageInputKeyEventLike = {
  key: string;
  altKey?: boolean;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  metaKey?: boolean;
};

export type ClipboardFileItemLike = {
  kind: string;
  getAsFile: () => File | null;
};

export const isSendShortcut = (event: MessageInputKeyEventLike): boolean => {
  return (
    event.key === "Enter" &&
    !event.altKey &&
    !event.ctrlKey &&
    !event.metaKey &&
    !event.shiftKey
  );
};

export const isNewLineShortcut = (
  event: MessageInputKeyEventLike
): boolean => {
  return event.key === "Enter" && (Boolean(event.altKey) || Boolean(event.ctrlKey));
};

const hasAllowedExtension = (fileName: string): boolean => {
  const lowerName = fileName.toLowerCase();
  for (const ext of ALLOWED_EXTENSIONS) {
    if (lowerName.endsWith(ext)) {
      return true;
    }
  }
  return false;
};

export const isSupportedUploadFile = (file: File): boolean => {
  const mime = (file.type || "").toLowerCase();
  if (mime) {
    if (ALLOWED_MIME_TYPES.has(mime)) {
      return true;
    }

    for (const prefix of ALLOWED_MIME_PREFIXES) {
      if (mime.startsWith(prefix)) {
        return true;
      }
    }
  }

  return hasAllowedExtension(file.name);
};

export const splitSupportedFiles = (files: File[]): {
  accepted: File[];
  rejected: File[];
} => {
  const accepted: File[] = [];
  const rejected: File[] = [];

  for (const file of files) {
    if (isSupportedUploadFile(file)) {
      accepted.push(file);
    } else {
      rejected.push(file);
    }
  }

  return { accepted, rejected };
};

export const extractClipboardFiles = (
  items: ArrayLike<ClipboardFileItemLike> | null | undefined
): File[] => {
  if (!items) {
    return [];
  }

  return Array.from(items)
    .filter((item) => item.kind === "file")
    .map((item) => item.getAsFile())
    .filter((file): file is File => Boolean(file));
};
