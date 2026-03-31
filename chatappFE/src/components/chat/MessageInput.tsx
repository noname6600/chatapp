import { useState, useRef, useEffect } from "react";
import { AlertCircle, Eye, FileText, Image as ImageIcon, LoaderCircle, Paperclip, Send, Video, X } from "lucide-react";
import ReplyPreview from "./ReplyPreview";
import EditPreview from "./EditPreview";
import MentionAutocomplete from "./MentionAutocomplete";
import DraftReviewModal from "./DraftReviewModal";
import {
  sendTyping,
  sendStopTyping,
} from "../../websocket/presence.socket";
import { getRoomMembers } from "../../api/room.service";
import { Button } from "../ui/Button";
import { useReply } from "../../hooks/useReply";
import { useEdit } from "../../hooks/useEdit";
import { useMention } from "../../hooks/useMention";
import { useUserStore } from "../../store/user.store";
import { editMessageApi } from "../../api/chat.service";
import { useChat } from "../../store/chat.store";
import { uploadChatAttachment } from "../../api/upload.service";
import {
  extractClipboardFiles,
  splitSupportedFiles,
  isNewLineShortcut,
  isSendShortcut,
} from "./messageInput.behavior";
import {
  appendTextBlock,
  buildMessageBlocks,
  createUploadingAssetPlaceholders,
  getAssetCount,
  hasFailedBlocks,
  hasUploadingBlocks,
  revokePreviewUrl,
  type DraftAssetBlock,
  type DraftBlock,
} from "./messageComposerDraft";
import type { Attachment } from "../../types/message";
import type { MentionSuggestion } from "./MentionAutocomplete";
import { buildMentionToken, extractMentionedUserIds } from "./mention.helpers";

interface Props {
  roomId: string;
}

const MAX_FILES = 5;
const MAX_SIZE = 10 * 1024 * 1024;

function getAttachmentIcon(type?: string) {
  if (type?.startsWith("image/")) return <ImageIcon size={18} />;
  if (type?.startsWith("video/")) return <Video size={18} />;
  return <FileText size={18} />;
}

export default function MessageInput({ roomId }: Props) {
  const [pendingText, setPendingText] = useState("");
  const [reviewText, setReviewText] = useState("");
  const [reviewOriginalText, setReviewOriginalText] = useState("");
  const [isReviewOpen, setIsReviewOpen] = useState(false);
  const [draftBlocks, setDraftBlocks] = useState<DraftBlock[]>([]);
  const [editingDraftBlockId, setEditingDraftBlockId] = useState<string | null>(null);
  const [editingDraftContent, setEditingDraftContent] = useState("");
  const [composerError, setComposerError] = useState<string | null>(null);
  const [uploadingCount, setUploadingCount] = useState(0);
  const [isDragOver, setIsDragOver] = useState(false);
  const [roomMemberIds, setRoomMemberIds] = useState<string[]>([]);
  const [pendingMentions, setPendingMentions] = useState<Record<string, MentionSuggestion>>({});
  const sendingRef = useRef(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const draftBlocksRef = useRef<DraftBlock[]>([]);

  const typingTimeoutRef = useRef<number | null>(null);
  const isTypingRef = useRef(false);

  const { replyingTo, clearReply } = useReply();
  const { editingMessage, clearEdit } = useEdit();
  const fetchUsers = useUserStore((s) => s.fetchUsers);
  const { sendMessage, currentUserId } = useChat();
  const {
    isOpen: mentionOpen,
    suggestions,
    selectedIndex,
    selectMention,
    handleKeyDown: handleMentionKeyDown,
    detectMention,
    setIsOpen: setMentionOpen,
  } = useMention({
    maxSuggestions: 5,
    candidateUserIds: roomMemberIds,
    currentUserId,
  });
  const users = useUserStore((s) => s.users);

  useEffect(() => {
    if (!roomId) {
      setRoomMemberIds([]);
      return;
    }

    let alive = true;

    void getRoomMembers(roomId)
      .then((members) => {
        if (!alive) return;
        const ids = members.map((member) => member.userId);
        setRoomMemberIds(ids);
        void fetchUsers(ids);
      })
      .catch(() => {
        if (!alive) return;
        setRoomMemberIds([]);
      });

    return () => {
      alive = false;
    };
  }, [roomId, fetchUsers]);

  // Load message content when editing starts
  useEffect(() => {
    if (editingMessage && editingMessage.content) {
      setPendingText(editingMessage.content);
      setDraftBlocks([]);
    }
  }, [editingMessage]);

  useEffect(() => {
    draftBlocksRef.current = draftBlocks;
  }, [draftBlocks]);

  useEffect(() => {
    if (!import.meta.env.DEV || !mentionOpen) {
      return;
    }

    const debugRows = suggestions.map((suggestion) => ({
      userId: suggestion.userId,
      username: suggestion.username,
      hasUsername: Boolean(suggestion.username?.trim()),
      displayName: suggestion.displayName,
      storeUsername: users[suggestion.userId]?.username || null,
    }));

    console.debug("[mention-debug] suggestions", {
      roomId,
      count: suggestions.length,
      rows: debugRows,
    });

    debugRows.forEach((row) => {
      console.log(
        `[mention-debug] id=${row.userId} username=${row.username || "<empty>"} displayName=${row.displayName || "<empty>"}`
      );
    });
  }, [mentionOpen, roomId, suggestions, users]);

  useEffect(() => {
    return () => {
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }
      stopTyping();
      draftBlocksRef.current.forEach(revokePreviewUrl);
    };
  }, []);

  const stopTyping = () => {
    if (!isTypingRef.current) return;

    sendStopTyping(roomId);
    isTypingRef.current = false;
  };

  const handleChange = (newText: string) => {
    setPendingText(newText);

    // Detect @ mentions
    detectMention(newText, newText.length);

    // Send typing indicator for non-empty content
    if (newText.trim().length > 0) {
      if (!isTypingRef.current) {
        sendTyping(roomId);
        isTypingRef.current = true;
      }

      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }

      typingTimeoutRef.current = window.setTimeout(() => {
        stopTyping();
      }, 1500);
    } else {
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }
      stopTyping();
    }
  };

  const insertMention = (suggestion: MentionSuggestion) => {
    const token = buildMentionToken(suggestion);

    if (import.meta.env.DEV) {
      console.debug("[mention-debug] select", {
        userId: suggestion.userId,
        username: suggestion.username,
        displayName: suggestion.displayName,
        builtToken: token,
      });
      console.log(
        `[mention-debug] selected id=${suggestion.userId} username=${suggestion.username || "<empty>"} displayName=${suggestion.displayName || "<empty>"}`
      );
    }

    if (!token.trim()) {
      return;
    }

    const atIndex = pendingText.lastIndexOf("@");
    if (atIndex === -1) {
      return;
    }

    const before = pendingText.substring(0, atIndex);
    const after = pendingText.substring(atIndex).replace(/^@\S*/, "");
    const newText = `${before}@${token} ${after}`;

    setPendingText(newText);

    setPendingMentions((prev) => ({
      ...prev,
      [suggestion.userId]: {
        ...suggestion,
        username: token,
        displayName: suggestion.displayName || suggestion.username || suggestion.userId,
      },
    }));

    setMentionOpen(false);
    textareaRef.current?.focus();
  };

  const applyMentionSelection = (suggestion: MentionSuggestion) => {
    selectMention(suggestion);
    insertMention(suggestion);
  };

  // Keyboard policy: Enter sends, Alt/Ctrl+Enter inserts newline.
  const handleTextareaKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (mentionOpen && suggestions.length > 0) {
      const result = handleMentionKeyDown(e);

      if (result && result !== "move-up" && result !== "move-down") {
        applyMentionSelection(result);
      }

      return;
    }

    if (isNewLineShortcut(e)) {
      return;
    }

    if (isSendShortcut(e)) {
      e.preventDefault();
      void send();
    }
  };

  const updateAssetBlock = (blockId: string, updater: (block: DraftAssetBlock) => DraftAssetBlock) => {
    setDraftBlocks((prev) =>
      prev.map((block) => {
        if (block.type !== "ASSET" || block.id !== blockId) {
          return block;
        }

        const next = updater(block);
        if (block.previewUrl && block.previewUrl !== next.previewUrl && next.status === "ready") {
          URL.revokeObjectURL(block.previewUrl);
        }
        return next;
      })
    );
  };

  const insertFilesIntoDraft = async (filesToInsert: File[]) => {
    setComposerError(null);

    const { accepted, rejected } = splitSupportedFiles(filesToInsert);
    if (rejected.length > 0) {
      setComposerError(`Unsupported file type: ${rejected.map((file) => file.name).join(", ")}`);
    }

    const currentAssetCount = getAssetCount(draftBlocksRef.current);
    if (currentAssetCount + accepted.length > MAX_FILES) {
      setComposerError(`Maximum ${MAX_FILES} files allowed`);
      return;
    }

    for (const file of accepted) {
      if (file.size > MAX_SIZE) {
        setComposerError(`File "${file.name}" exceeds ${(MAX_SIZE / 1024 / 1024).toFixed(1)}MB limit`);
        return;
      }
    }

    if (accepted.length === 0) {
      return;
    }

    const placeholders = createUploadingAssetPlaceholders(
      draftBlocksRef.current,
      pendingText,
      accepted
    );

    setDraftBlocks((prev) => [...appendTextBlock(prev, pendingText), ...placeholders]);
    setPendingText("");
    setMentionOpen(false);
    stopTyping();
    setUploadingCount((count) => count + placeholders.length);

    placeholders.forEach((placeholder, index) => {
      const file = accepted[index];
      void (async () => {
        try {
          const attachment = await uploadChatAttachment(file);
          updateAssetBlock(placeholder.id, (block) => ({
            ...block,
            status: "ready",
            attachment,
          }));
        } catch (error) {
          const message = error instanceof Error ? error.message : `Failed to upload ${file.name}`;
          updateAssetBlock(placeholder.id, (block) => ({
            ...block,
            status: "failed",
            error: message,
          }));
          setComposerError(message);
        } finally {
          setUploadingCount((count) => Math.max(0, count - 1));
        }
      })();
    });
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();

    const nextTarget = e.relatedTarget as Node | null;
    if (!nextTarget || !e.currentTarget.contains(nextTarget)) {
      setIsDragOver(false);
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);

    const droppedFiles = Array.from(e.dataTransfer.files ?? []);
    if (droppedFiles.length > 0) {
      void insertFilesIntoDraft(droppedFiles);
    }
  };

  const handleManualFilePick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = Array.from(e.target.files ?? []);
    if (selectedFiles.length > 0) {
      void insertFilesIntoDraft(selectedFiles);
    }
    e.target.value = "";
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const pastedFiles = extractClipboardFiles(e.clipboardData.items);

    if (pastedFiles.length === 0) {
      return;
    }

    e.preventDefault();
    void insertFilesIntoDraft(pastedFiles);
  };

  const removeDraftBlock = (blockId: string) => {
    setDraftBlocks((prev) => {
      const next = prev.filter((block) => {
        if (block.id !== blockId) {
          return true;
        }

        revokePreviewUrl(block);
        return false;
      });

      return next;
    });

    if (editingDraftBlockId === blockId) {
      setEditingDraftBlockId(null);
      setEditingDraftContent("");
    }
  };

  const applyDraftTextEdit = (blocks: DraftBlock[], blockId: string, content: string): DraftBlock[] =>
    blocks.map((block) =>
      block.type === "TEXT" && block.id === blockId
        ? {
            ...block,
            text: content,
          }
        : block
    );

  const startEditDraftText = (blockId: string, text: string) => {
    if (editingDraftBlockId && editingDraftBlockId !== blockId) {
      setDraftBlocks((prev) => applyDraftTextEdit(prev, editingDraftBlockId, editingDraftContent));
    }

    setEditingDraftBlockId(blockId);
    setEditingDraftContent(text);
  };

  const saveEditDraftText = () => {
    if (!editingDraftBlockId) {
      return;
    }

    setDraftBlocks((prev) => applyDraftTextEdit(prev, editingDraftBlockId, editingDraftContent));
    setEditingDraftBlockId(null);
    setEditingDraftContent("");
  };

  const cancelEditDraftText = () => {
    setEditingDraftBlockId(null);
    setEditingDraftContent("");
  };

  const handleDraftEditKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Escape") {
      e.preventDefault();
      cancelEditDraftText();
      return;
    }

    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      saveEditDraftText();
    }
  };

  const openDraftReview = () => {
    if (editingDraftBlockId) {
      setDraftBlocks((prev) => applyDraftTextEdit(prev, editingDraftBlockId, editingDraftContent));
      setEditingDraftBlockId(null);
      setEditingDraftContent("");
    }

    setReviewText(pendingText);
    setReviewOriginalText(pendingText);
    setIsReviewOpen(true);
  };

  const closeDraftReview = () => {
    setIsReviewOpen(false);
  };

  const restoreReviewText = () => {
    setReviewText(reviewOriginalText);
  };

  const send = async (textOverride?: string) => {
    const textForSend = textOverride ?? pendingText;
    const draftBlocksForSend = editingDraftBlockId
      ? applyDraftTextEdit(draftBlocks, editingDraftBlockId, editingDraftContent)
      : draftBlocks;

    const messageBlocks = buildMessageBlocks(draftBlocksForSend, textForSend);
    const attachments = messageBlocks
      .filter((block) => block.type === "ASSET" && block.attachment)
      .map((block) => block.attachment as Attachment);
    const content = messageBlocks
      .filter((block) => block.type === "TEXT")
      .map((block) => block.text?.trim() ?? "")
      .filter(Boolean)
      .join(" ");
    const mentionedUserIds = extractMentionedUserIds(
      content,
      Object.values(pendingMentions).map((mention) => ({
        userId: mention.userId,
        token: mention.username,
      }))
    );

    if ((content.length === 0 && attachments.length === 0) || sendingRef.current || uploadingCount > 0) {
      return;
    }

    if (hasFailedBlocks(draftBlocksForSend)) {
      setComposerError("Remove failed uploads before sending");
      return;
    }

    sendingRef.current = true;

    try {
      if (editingMessage) {
        // Edit existing message
        await editMessageApi(editingMessage.messageId, textForSend.trim());
        clearEdit();
      } else {
        await sendMessage(
          roomId,
          content,
          attachments,
          replyingTo?.messageId ?? null,
          messageBlocks,
          mentionedUserIds
        );
        clearReply();
      }

      setPendingText("");
      setReviewText("");
      setReviewOriginalText("");
      setIsReviewOpen(false);
      draftBlocksRef.current.forEach(revokePreviewUrl);
      setDraftBlocks([]);
      setPendingMentions({});
      setComposerError(null);
      setMentionOpen(false);
      setEditingDraftBlockId(null);
      setEditingDraftContent("");
      stopTyping();
    } finally {
      setTimeout(() => {
        sendingRef.current = false;
      }, 200);
    }
  };

  return (
    <div className="shrink-0 p-3 border-t bg-white space-y-2">
      <DraftReviewModal
        isOpen={isReviewOpen}
        draftText={reviewText}
        originalText={reviewOriginalText}
        attachmentCount={getAssetCount(draftBlocks)}
        replyToMessageId={replyingTo?.messageId}
        sending={sendingRef.current}
        onDraftTextChange={setReviewText}
        onCancel={closeDraftReview}
        onRestoreOriginal={restoreReviewText}
        onSend={() => {
          void send(reviewText);
        }}
      />

      {editingMessage && (
        <EditPreview
          message={editingMessage}
          senderName={users[editingMessage.senderId]?.displayName || "Unknown"}
          onClear={clearEdit}
        />
      )}

      {replyingTo && !editingMessage && (
        <ReplyPreview
          message={replyingTo}
          senderName={users[replyingTo.senderId]?.displayName || users[replyingTo.senderId]?.username || "Unknown"}
          senderAvatar={users[replyingTo.senderId]?.avatarUrl ?? null}
          currentUserId={currentUserId}
          isMissingOriginal={replyingTo.deleted}
          onClear={clearReply}
        />
      )}

      {composerError && (
        <div className="px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          {composerError}
        </div>
      )}

      {draftBlocks.length > 0 && (
        <div className="space-y-2 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div className="text-xs font-semibold uppercase tracking-wide text-gray-500">
            Draft Preview
          </div>
          <div className="space-y-2">
            {draftBlocks.map((block) => (
              block.type === "TEXT" ? (
                editingDraftBlockId === block.id ? (
                  <textarea
                    key={block.id}
                    autoFocus
                    value={editingDraftContent}
                    onChange={(e) => setEditingDraftContent(e.target.value)}
                    onBlur={saveEditDraftText}
                    onKeyDown={handleDraftEditKeyDown}
                    className="w-full rounded-lg bg-white px-3 py-2 text-sm text-gray-800 whitespace-pre-wrap break-words border border-blue-300 focus:border-blue-400 focus:outline-none"
                    rows={Math.max(2, editingDraftContent.split("\n").length)}
                  />
                ) : (
                  <div
                    key={block.id}
                    onClick={() => startEditDraftText(block.id, block.text)}
                    className="rounded-lg bg-white px-3 py-2 text-sm text-gray-800 whitespace-pre-wrap break-words border border-gray-200 cursor-text"
                    title="Click to edit"
                  >
                    {block.text}
                  </div>
                )
              ) : (
                <div
                  key={block.id}
                  className={`flex items-start gap-3 rounded-lg border px-3 py-2 ${
                    block.status === "failed"
                      ? "border-red-200 bg-red-50"
                      : block.status === "uploading"
                        ? "border-blue-200 bg-blue-50"
                        : "border-gray-200 bg-white"
                  }`}
                >
                  <div className="flex h-14 w-14 items-center justify-center overflow-hidden rounded-md bg-gray-100 text-gray-500">
                    {block.status === "ready" && block.attachment?.type === "IMAGE" ? (
                      <img
                        src={block.attachment.url}
                        alt={block.fileName}
                        className="h-full w-full object-cover"
                      />
                    ) : block.previewUrl ? (
                      <img
                        src={block.previewUrl}
                        alt={block.fileName}
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      getAttachmentIcon(block.mimeType)
                    )}
                  </div>

                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 text-sm font-medium text-gray-900">
                      <span className="truncate">{block.fileName}</span>
                      {block.status === "uploading" && (
                        <span className="inline-flex items-center gap-1 text-xs text-blue-600">
                          <LoaderCircle size={14} className="animate-spin" />
                          Uploading
                        </span>
                      )}
                      {block.status === "failed" && (
                        <span className="inline-flex items-center gap-1 text-xs text-red-600">
                          <AlertCircle size={14} />
                          Failed
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-gray-500">
                      {block.size ? `${(block.size / 1024).toFixed(2)} KB` : "Attachment"}
                    </div>
                    {block.status === "failed" && block.error && (
                      <div className="mt-1 text-xs text-red-600">{block.error}</div>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={() => removeDraftBlock(block.id)}
                    className="rounded p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
                    title="Remove attachment"
                  >
                    <X size={16} />
                  </button>
                </div>
              )
            ))}

            {pendingText.trim().length > 0 && (
              <div className="rounded-lg border border-dashed border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 whitespace-pre-wrap break-words">
                {pendingText}
              </div>
            )}
          </div>
        </div>
      )}

      <div
        className="relative"
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        <div
          className={[
            "rounded-lg border bg-white p-2 transition-colors",
            isDragOver ? "border-blue-500 bg-blue-50" : "border-gray-300",
          ].join(" ")}
        >
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/*,video/*,.pdf,.doc,.docx,.txt"
            className="hidden"
            onChange={handleManualFilePick}
            disabled={uploadingCount > 0}
          />

          <div className="flex items-end gap-2">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              disabled={uploadingCount > 0}
              onClick={() => fileInputRef.current?.click()}
              title="Attach files"
              aria-label="Attach files"
            >
              <Paperclip size={18} />
            </Button>

            <textarea
              ref={textareaRef}
              value={pendingText}
              onChange={(e) => handleChange(e.target.value)}
              onKeyDown={handleTextareaKeyDown}
              onPaste={handlePaste}
              placeholder="Type a message, then paste or attach images/files (Enter to send, Alt+Enter for new line)"
              className="min-h-12 max-h-40 w-full resize-none rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none focus:border-blue-400"
              rows={2}
            />

            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={openDraftReview}
              disabled={
                (pendingText.trim().length === 0 && draftBlocks.length === 0) ||
                sendingRef.current ||
                uploadingCount > 0 ||
                hasUploadingBlocks(draftBlocks) ||
                hasFailedBlocks(draftBlocks)
              }
              className="gap-2"
            >
              <Eye size={16} />
              Review
            </Button>

            <Button
              onClick={() => void send()}
              disabled={
                (pendingText.trim().length === 0 && draftBlocks.length === 0) ||
                sendingRef.current ||
                uploadingCount > 0 ||
                hasUploadingBlocks(draftBlocks) ||
                hasFailedBlocks(draftBlocks)
              }
              size="sm"
              className="gap-2"
            >
              <Send size={16} />
              Send
            </Button>
          </div>
        </div>

        {/* Mention autocomplete */}
        <MentionAutocomplete
          suggestions={suggestions}
          isOpen={mentionOpen}
          selectedIndex={selectedIndex}
          onSelect={(suggestion) => {
            applyMentionSelection(suggestion);
          }}
        />
      </div>
    </div>
  );
}