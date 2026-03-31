import type { Attachment, ChatMessage } from "../types/message";

export const MISSING_ORIGINAL_MESSAGE_TEXT = "cannot load the original message";
export const UNLOADED_ORIGINAL_MESSAGE_TEXT = "load original context";

export type ReplyPreviewKind = "text" | "media" | "missing" | "unloaded";

export interface ReplyPreviewModel {
  senderName: string;
  senderAvatar: string | null;
  previewText: string;
  kind: ReplyPreviewKind;
  isOwnTarget: boolean;
  isMissingOriginal: boolean;
}

interface BuildReplyPreviewModelOptions {
  message: ChatMessage | null;
  senderName?: string | null;
  senderAvatar?: string | null;
  currentUserId?: string | null;
  isMissingOriginal?: boolean;
}

function summarizeAttachments(attachments: Attachment[]): string {
  if (!attachments.length) return "Attachment";

  const imageCount = attachments.filter((a) => a.type === "IMAGE").length;
  const videoCount = attachments.filter((a) => a.type === "VIDEO").length;
  const fileCount = attachments.filter((a) => a.type === "FILE").length;

  if (imageCount > 0) {
    return imageCount > 1 ? `${imageCount} images` : "Image";
  }

  if (videoCount > 0) {
    return videoCount > 1 ? `${videoCount} videos` : "Video";
  }

  if (fileCount > 0) {
    return fileCount > 1 ? `${fileCount} files` : "File";
  }

  return "Attachment";
}

export function buildReplyPreviewModel({
  message,
  senderName,
  senderAvatar = null,
  currentUserId = null,
  isMissingOriginal = false,
}: BuildReplyPreviewModelOptions): ReplyPreviewModel {
  const normalizedSenderName = senderName?.trim() || "Unknown";

  if (!message && !isMissingOriginal) {
    return {
      senderName: normalizedSenderName,
      senderAvatar,
      previewText: UNLOADED_ORIGINAL_MESSAGE_TEXT,
      kind: "unloaded",
      isOwnTarget: false,
      isMissingOriginal: false,
    };
  }

  if (!message || message.deleted || isMissingOriginal) {
    return {
      senderName: normalizedSenderName,
      senderAvatar,
      previewText: MISSING_ORIGINAL_MESSAGE_TEXT,
      kind: "missing",
      isOwnTarget: false,
      isMissingOriginal: true,
    };
  }

  const contentText = message.content?.trim();

  if (contentText) {
    return {
      senderName: normalizedSenderName,
      senderAvatar,
      previewText: contentText,
      kind: "text",
      isOwnTarget: Boolean(currentUserId && currentUserId === message.senderId),
      isMissingOriginal: false,
    };
  }

  return {
    senderName: normalizedSenderName,
    senderAvatar,
    previewText: summarizeAttachments(message.attachments ?? []),
    kind: "media",
    isOwnTarget: Boolean(currentUserId && currentUserId === message.senderId),
    isMissingOriginal: false,
  };
}
