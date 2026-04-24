import { useMemo, useState, useRef, useEffect } from "react";
import type { ReactNode } from "react";
import { Edit2, Trash2, Reply, X, Check, CornerUpLeft, Pin, Forward } from "lucide-react";
import MentionAutocomplete from "./MentionAutocomplete";
import type { MentionSuggestion } from "./MentionAutocomplete";
import { useMention } from "../../hooks/useMention";
import { buildMentionToken } from "./mention.helpers";
import type { ChatMessage, MessageBlock } from "../../types/message";
import UserAvatar from "../user/UserAvatar";
import Username from "../user/Username";
import AttachmentDisplay from "./AttachmentDisplay";
import MessageBlocks, { getRenderableBlocks } from "./MessageBlocks";
import ReactionGroup from "./ReactionGroup";
import EmojiPicker from "./EmojiPicker";
import { BlockMessageEditor } from "./BlockMessageEditor";
import EditedIndicator from "./EditedIndicator";
import { useReaction } from "../../hooks/useReaction";
import { useReply } from "../../hooks/useReply";
import { useDelete } from "../../hooks/useDelete";
import { editMessageApi } from "../../api/chat.service";
import { getRoomMembers } from "../../api/room.service";
import { useChat } from "../../store/chat.store";
import { useUserStore } from "../../store/user.store";
import {
  formatMessageTimeShort,
  formatMessageTimestamp,
} from "../../utils/messageTimestamp";
import { buildReplyPreviewModel } from "../../utils/replyPreview";

interface MessageItemProps {
  message: ChatMessage;
  repliedMessage?: ChatMessage | null;
  repliedMessageId?: string | null;
  isReplyTargetUnavailable?: boolean;
  onShowDeleteDialog?: () => void;
  onJumpToMessage?: (messageId: string) => void;
  showUserInfo?: boolean;
  isGrouped?: boolean;
  indexInGroup?: number;
  isReplyLinkedHighlight?: boolean;
  isJumpTarget?: boolean;
  isPinned?: boolean;
  onPin?: (message: ChatMessage) => void;
  onForward?: (message: ChatMessage) => void;
}

const MENTION_TOKEN_PATTERN = /(^|\s)(@[A-Za-z0-9_.-]+)/g;

const normalizeMentionToken = (value: string): string =>
  value
    .trim()
    .replace(/^@+/, "")
    .replace(/\s+/g, "_")
    .toLowerCase();

const renderContentWithMentions = (
  content: string,
  resolveMentionLabel?: (token: string) => string,
  resolveMentionUserId?: (token: string) => string | null
): ReactNode[] => {
  const chunks: ReactNode[] = [];
  let cursor = 0;
  let tokenIndex = 0;

  for (const match of content.matchAll(MENTION_TOKEN_PATTERN)) {
    const matchIndex = match.index ?? 0;
    const leading = match[1] ?? "";
    const mention = match[2] ?? "";
    const mentionToken = mention.startsWith("@") ? mention.slice(1) : mention;
    const mentionLabel = resolveMentionLabel
      ? `@${resolveMentionLabel(mentionToken)}`
      : mention;
    const mentionStart = matchIndex + leading.length;

    if (matchIndex > cursor) {
      chunks.push(content.slice(cursor, matchIndex));
    }

    if (leading.length > 0) {
      chunks.push(leading);
    }

    const mentionUserId = resolveMentionUserId?.(mentionToken) ?? null;
    const mentionNode = (
      <span className="rounded bg-amber-100/80 px-0.5 font-medium text-amber-800">
        {mentionLabel}
      </span>
    );

    chunks.push(
      mentionUserId ? (
        <Username key={`mention-${tokenIndex}`} userId={mentionUserId} source="MENTION">
          {mentionNode}
        </Username>
      ) : (
        <span key={`mention-${tokenIndex}`}>{mentionNode}</span>
      )
    );

    cursor = mentionStart + mention.length;
    tokenIndex += 1;
  }

  if (cursor < content.length) {
    chunks.push(content.slice(cursor));
  }

  return chunks.length > 0 ? chunks : [content];
};

export default function MessageItem({
  message: m,
  repliedMessage,
  repliedMessageId,
  isReplyTargetUnavailable = false,
  onShowDeleteDialog,
  onJumpToMessage,
  showUserInfo = false,
  isGrouped = false,
  indexInGroup = 0,
  isReplyLinkedHighlight = false,
  isJumpTarget = false,
  isPinned = false,
  onPin,
  onForward,
}: MessageItemProps) {
  const { upsertMessage, currentUserId, retryMessage } = useChat();
  const users = useUserStore((s) => s.users);
  const fetchUsers = useUserStore((s) => s.fetchUsers);
  const [isEditing, setIsEditing] = useState(false);
  const [editContent, setEditContent] = useState(m.content || "");
  const [editLoading, setEditLoading] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [editingBlocks, setEditingBlocks] = useState<MessageBlock[] | null>(null);
  const [mentionCandidateIds, setMentionCandidateIds] = useState<string[]>([]);

  useEffect(() => {
    if (!isEditing || !m.roomId) return;

    let alive = true;

    void getRoomMembers(m.roomId)
      .then((members) => {
        if (!alive) return;
        const ids = members.map((member) => member.userId);
        setMentionCandidateIds(ids);
        void fetchUsers(ids);
      })
      .catch((_error) => {
        if (!alive) return;
        setMentionCandidateIds([]);
      });

    return () => {
      alive = false;
    };
  }, [isEditing, m.roomId, fetchUsers]);

  const handleEditChange = (value: string) => {
    if (editError) setEditError(null);
    setEditContent(value);
  };

  const { toggleReaction, loading: reactionLoading } = useReaction({
    messageId: m.messageId,
    roomId: m.roomId,
  });
  const { setReply } = useReply();
  const { setDeleting } = useDelete();

  const repliedToUser = useMemo(
    () => (repliedMessage ? users[repliedMessage.senderId] : null),
    [repliedMessage, users]
  );

  const hasReplyReference = Boolean(repliedMessageId);
  const replyPreview = useMemo(
    () =>
      buildReplyPreviewModel({
        message: repliedMessage ?? null,
        senderName:
          repliedToUser?.displayName || repliedToUser?.username || "Unknown",
        senderAvatar: repliedToUser?.avatarUrl ?? null,
        currentUserId,
        isMissingOriginal:
          hasReplyReference &&
          !repliedMessage &&
          isReplyTargetUnavailable,
      }),
    [
      repliedMessage,
      repliedToUser,
      currentUserId,
      hasReplyReference,
      isReplyTargetUnavailable,
    ]
  );

  const resolvedCurrentUserId = currentUserId ?? localStorage.getItem("my_user_id");
  const isOwnMessage = m.senderId === resolvedCurrentUserId;
  const canUseMessageActions = !m.deleted && m.type !== "SYSTEM";
  const messageBlocks = m.blocks ?? [];
  const hasStructuredBlocks = messageBlocks.length > 0;
  const renderableMessageBlocks = getRenderableBlocks(messageBlocks);
  const singleTextBlock =
    renderableMessageBlocks.length === 1 &&
    renderableMessageBlocks[0].type === "TEXT"
      ? renderableMessageBlocks[0]
      : null;
  // Allow editing for both TEXT and MIXED messages. ATTACHMENT-only messages cannot be edited.
  const canEditMessage = m.type !== "ATTACHMENT"

  const resolveMentionLabel = useMemo(() => {
    const lookup = new Map<string, string>();
    const idLookup = new Map<string, string>();

    Object.values(users).forEach((user) => {
      if (!user) return;

      const label = (user.displayName || user.username || user.accountId || "").trim();
      if (!label) return;

      const usernameKey = normalizeMentionToken(user.username || "");
      if (usernameKey) {
        lookup.set(usernameKey, label);
        idLookup.set(usernameKey, user.accountId);
      }

      const displayNameKey = normalizeMentionToken(user.displayName || "");
      if (displayNameKey) {
        lookup.set(displayNameKey, label);
        idLookup.set(displayNameKey, user.accountId);
      }
    });

    const resolveLabel = (token: string): string => {
      const normalized = normalizeMentionToken(token);
      if (!normalized) return token;

      return lookup.get(normalized) || token;
    };

    const resolveUserId = (token: string): string | null => {
      const normalized = normalizeMentionToken(token);
      if (!normalized) return null;

      return idLookup.get(normalized) || null;
    };

    return {
      resolveLabel,
      resolveUserId,
    };
  }, [users]);

  const handleEditSubmit = async () => {
    if (!editContent.trim()) return;
    if (editContent === m.content) {
      setIsEditing(false);
      return;
    }

    const nextContent = editContent.trim();
    const previousMessage = m;
    const optimisticEditedAt = new Date().toISOString();

    setEditLoading(true);
    setEditError(null);

    const updatedBlocks = singleTextBlock
      ? [{ ...singleTextBlock, text: nextContent }]
      : m.blocks;

    upsertMessage({
      ...m,
      content: nextContent,
      blocks: updatedBlocks,
      editedAt: optimisticEditedAt,
    });
    setIsEditing(false);

    try {
      const updated = singleTextBlock
        ? await editMessageApi(m.messageId, nextContent, updatedBlocks ?? undefined)
        : await editMessageApi(m.messageId, nextContent);
      // Preserve original message entirely, only update content and metadata from server.
      // Author information (senderId, etc.) must remain unchanged - only content is mutable.
      upsertMessage({
        ...m,
        ...updated,
        // Explicitly preserve author metadata - these are immutable
        senderId: m.senderId,
        messageId: m.messageId,
        roomId: m.roomId,
        seq: m.seq,
        type: m.type,
        createdAt: m.createdAt,
      });

    } catch (error) {
      console.error("Edit failed:", error);
      upsertMessage(previousMessage);
      setEditContent(previousMessage.content || "");
      setEditError(error instanceof Error ? error.message : "Failed to save message edits");
      setIsEditing(true);
    } finally {
      setEditLoading(false);
    }
  };

  const handleBlocksEditSubmit = async (blocks: MessageBlock[], content: string) => {
    const previousMessage = m;
    const optimisticEditedAt = new Date().toISOString();

    setEditLoading(true);
    setEditError(null);

    // Optimistic update - show new blocks immediately
    upsertMessage({
      ...m,
      content,
      blocks,
      editedAt: optimisticEditedAt,
    });
    setIsEditing(false);
    setEditingBlocks(null);

    try {
      // Send blocks to backend to preserve block structure
      const updated = await editMessageApi(m.messageId, content, blocks);
      
      // Merge response with message, but preserve original author/structural metadata
      upsertMessage({
        ...m,
        ...updated,
        senderId: m.senderId,
        messageId: m.messageId,
        roomId: m.roomId,
        seq: m.seq,
        type: m.type,
        createdAt: m.createdAt,
      });
    } catch (error) {
      console.error("Edit failed:", error);
      // Restore previous message on error
      upsertMessage(previousMessage);
      setEditError(error instanceof Error ? error.message : "Failed to save message edits");
      setIsEditing(true);
      setEditingBlocks(messageBlocks);
    } finally {
      setEditLoading(false);
    }
  };

  const handleEditCancel = () => {
    setEditContent(m.content || "");
    setEditError(null);
    setIsEditing(false);
    setEditingBlocks(null);
  };

  return (
    <div
      id={`message-${m.messageId}`}
      data-message-id={m.messageId}
      className={`flex group relative ${
        isGrouped ? "gap-1.5" : "gap-2"
      } ${
        isGrouped && indexInGroup > 0 ? "-mt-0.5" : ""
      } ${
        isReplyLinkedHighlight ? "rounded-md bg-amber-50/60 px-1 py-1" : ""
      } ${
        isJumpTarget
          ? "rounded-md bg-yellow-50/80 ring-1 ring-yellow-200 shadow-[0_0_0_1px_rgba(250,204,21,0.25)] px-1 py-1 transition-colors duration-300"
          : ""
      }`}
    >
      {/* Left Column - Avatar or Time space */}
      {isGrouped ? (
        <div className="relative w-10 flex-shrink-0">
          {/* First message shows avatar */}
          {indexInGroup === 0 ? (
            <UserAvatar
              userId={m.senderId}
              avatar={users[m.senderId]?.avatarUrl}
              size={32}
            />
          ) : (
            /* Subsequent messages show time on hover */
            <div className="relative h-0">
              <div className="absolute right-0 top-0 text-xs text-gray-400 opacity-0 group-hover:opacity-100 transition whitespace-nowrap">
                {formatMessageTimeShort(m.createdAt)}
              </div>
            </div>
          )}
        </div>
      ) : (
        /* Non-grouped messages */
        <div className="w-10 flex-shrink-0 flex items-start">
          <UserAvatar
            userId={m.senderId}
            avatar={users[m.senderId]?.avatarUrl}
            size={32}
          />
        </div>
      )}

      {/* Right Column - User info and content */}
      <div className={`flex flex-col min-w-0 flex-1 transition-opacity duration-200 ${m.deliveryStatus === "pending" ? "opacity-60" : ""}`}>
        <MessageHeader
          show={showUserInfo}
          userId={m.senderId}
          displayName={
            users[m.senderId]?.displayName || users[m.senderId]?.username || "Unknown"
          }
          createdAt={m.createdAt}
          deliveryStatus={m.deliveryStatus}
          onRetry={() => void retryMessage(m.roomId, m.messageId)}
          onDelete={() => {
            setDeleting(m.messageId, m.content || "");
            onShowDeleteDialog?.();
          }}
        />

        {isEditing ? (
          editingBlocks !== null ? (
            <BlockMessageEditor
              blocks={editingBlocks}
              onSave={handleBlocksEditSubmit}
              onCancel={handleEditCancel}
              isSaving={editLoading}
              error={editError}
              currentUserId={currentUserId}
              candidateUserIds={mentionCandidateIds}
            />
          ) : (
            <InlineEditInput
              value={editContent}
              onChange={handleEditChange}
              onSubmit={handleEditSubmit}
              onCancel={handleEditCancel}
              editLoading={editLoading}
              editError={editError}
              currentUserId={currentUserId}
              candidateUserIds={mentionCandidateIds}
            />
          )
        ) : (
          <MessageContent
            content={m.content ?? ""}
            editedAt={m.editedAt}
            replyPreview={
              hasReplyReference
                ? {
                    senderId: repliedMessage?.senderId ?? "unknown",
                    senderAvatar: replyPreview.senderAvatar,
                    repliedToName: replyPreview.senderName,
                    previewText: replyPreview.previewText,
                    kind: replyPreview.kind,
                    isOwnTarget: replyPreview.isOwnTarget,
                    isMissingOriginal: replyPreview.isMissingOriginal,
                    repliedMessageId: repliedMessageId ?? null,
                  }
                : null
            }
            blocks={m.blocks ?? []}
            forwardedFromMessageId={m.forwardedFromMessageId ?? null}
            resolveMentionLabel={resolveMentionLabel.resolveLabel}
            resolveMentionUserId={resolveMentionLabel.resolveUserId}
            onJumpToMessage={onJumpToMessage}
          />
        )}

        {/* Attachments */}
        {!hasStructuredBlocks && <AttachmentDisplay attachments={m.attachments} />}

        {/* Reactions */}
        {m.reactions && m.reactions.length > 0 && (
          <ReactionGroup
            reactions={m.reactions}
            onReactionClick={toggleReaction}
            disabled={reactionLoading}
            currentUserId={currentUserId}
          />
        )}
      </div>

      {/* Message Actions - Top Right (always visible when editing) */}
      <MessageActions
        isOwnMessage={isOwnMessage}
        isEditing={isEditing}
        forceVisible={isJumpTarget}
        canEdit={canEditMessage}
        reactionLoading={reactionLoading}
        onReply={() => setReply(m)}
        onEdit={() => {
          setEditContent(singleTextBlock?.text || m.content || "");
          setIsEditing(true);
          // Use block editor if message has structured blocks, regardless of type
          // This handles MIXED messages and any message with interleaved text/media
          if (messageBlocks.length > 0 && !singleTextBlock) {
            setEditingBlocks(messageBlocks);
          } else {
            setEditingBlocks(null);
          }
        }}
        onDelete={() => {
          setDeleting(m.messageId, m.content || "");
          onShowDeleteDialog?.();
        }}
        onPin={() => onPin?.(m)}
        onForward={() => onForward?.(m)}
        onEmojiSelect={toggleReaction}
        canUseMessageActions={canUseMessageActions}
        isPinned={isPinned}
      />
    </div>
  );
}

function MessageHeader({
  show,
  userId,
  displayName,
  createdAt,
  deliveryStatus,
  onRetry,
  onDelete,
}: {
  show: boolean;
  userId: string;
  displayName: string;
  createdAt: string;
  deliveryStatus?: string;
  onRetry?: () => void;
  onDelete?: () => void;
}) {
  if (!show) {
    // For grouped messages (header hidden), only show failed state (not pending —
    // pending uses opacity on the wrapper and adds no height).
    if (deliveryStatus !== "failed") return null;
    return (
      <div className="flex flex-wrap items-center gap-1 mb-0">
        <span className="flex flex-wrap items-center gap-1 text-xs text-red-500">
          <span className="whitespace-nowrap">Failed to send</span>
          <button
            type="button"
            onClick={onRetry}
            className="rounded px-1.5 py-0.5 border border-red-200 hover:bg-red-50 whitespace-nowrap"
          >
            Retry
          </button>
          <button
            type="button"
            onClick={onDelete}
            className="rounded px-1.5 py-0.5 border border-red-200 hover:bg-red-50 text-red-500 hover:text-red-700 whitespace-nowrap"
            title="Delete failed message"
          >
            Delete
          </button>
        </span>
      </div>
    );
  }

  return (
    <div className="flex flex-wrap items-baseline gap-2 mb-0">
      <Username userId={userId}>
        <span className="font-semibold text-sm text-gray-900">{displayName}</span>
      </Username>
      {/* Pending: amber timestamp — same space as normal, zero height change */}
      <span
        className={`text-xs whitespace-nowrap ${
          deliveryStatus === "pending"
            ? "text-amber-400"
            : deliveryStatus === "failed"
              ? "text-red-400"
              : "text-gray-400"
        }`}
        title={new Date(createdAt).toLocaleString()}
      >
        {formatMessageTimestamp(createdAt)}
      </span>
      {deliveryStatus === "failed" && (
        <span className="flex flex-wrap items-center gap-1 text-xs text-red-500">
          <span className="whitespace-nowrap">Failed to send</span>
          <button
            type="button"
            onClick={onRetry}
            className="rounded px-1.5 py-0.5 border border-red-200 hover:bg-red-50 whitespace-nowrap"
          >
            Retry
          </button>
          <button
            type="button"
            onClick={onDelete}
            className="rounded px-1.5 py-0.5 border border-red-200 hover:bg-red-50 text-red-500 hover:text-red-700 whitespace-nowrap"
            title="Delete failed message"
          >
            Delete
          </button>
        </span>
      )}
    </div>
  );
}

function InlineEditInput({
  value,
  onChange,
  onSubmit,
  onCancel,
  editLoading,
  editError,
  currentUserId,
  candidateUserIds,
}: {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  onCancel: () => void;
  editLoading: boolean;
  editError: string | null;
  currentUserId: string | null;
  candidateUserIds: string[];
}) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const {
    isOpen: mentionOpen,
    suggestions: mentionSuggestions,
    selectedIndex: mentionSelectedIndex,
    detectMention,
    selectMention,
    handleKeyDown: handleMentionKeyDown,
    setIsOpen: setMentionOpen,
  } = useMention({ maxSuggestions: 5, currentUserId, candidateUserIds });

  useEffect(() => {
    if (textareaRef.current) {
      const tx = textareaRef.current;
      tx.style.height = "auto";
      tx.style.height = `${tx.scrollHeight}px`;
      tx.focus();
      // Move cursor to end
      tx.selectionStart = tx.selectionEnd = tx.value.length;
    }
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    onChange(e.target.value);
    detectMention(e.target.value, e.target.selectionStart ?? e.target.value.length);
    const tx = e.target;
    tx.style.height = "auto";
    tx.style.height = `${tx.scrollHeight}px`;
  };

  const insertMention = (suggestion: MentionSuggestion) => {
    const token = buildMentionToken(suggestion);
    if (!token.trim()) return;

    const tx = textareaRef.current;
    const cursor = tx?.selectionStart ?? value.length;
    const beforeCursor = value.substring(0, cursor);
    const atIndex = beforeCursor.lastIndexOf("@");
    if (atIndex === -1) return;

    const before = value.substring(0, atIndex);
    const after = value.substring(cursor).replace(/^\S*/, "");
    const nextValue = `${before}@${token} ${after}`;
    const nextCursor = `${before}@${token} `.length;

    onChange(nextValue);
    selectMention(suggestion);

    requestAnimationFrame(() => {
      if (!textareaRef.current) return;
      textareaRef.current.focus();
      textareaRef.current.selectionStart = nextCursor;
      textareaRef.current.selectionEnd = nextCursor;
      detectMention(nextValue, nextCursor);
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (mentionOpen && mentionSuggestions.length > 0) {
      const result = handleMentionKeyDown(e);
      if (result && result !== "move-up" && result !== "move-down") {
        insertMention(result as MentionSuggestion);
      }
      return;
    }

    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      onSubmit();
      return;
    }

    if (e.key === "Escape") {
      setMentionOpen(false);
      onCancel();
    }
  };

  const handleMentionSelect = (suggestion: MentionSuggestion) => {
    insertMention(suggestion);
  };

  return (
    <div className="relative flex flex-col gap-1">
      {mentionOpen && mentionSuggestions.length > 0 && (
        <div className="absolute top-full left-0 right-0 mt-1 z-20">
          <MentionAutocomplete
            suggestions={mentionSuggestions}
            isOpen={mentionOpen}
            selectedIndex={mentionSelectedIndex}
            onSelect={handleMentionSelect}
          />
        </div>
      )}
      <textarea
        ref={textareaRef}
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        disabled={editLoading}
        rows={1}
        className="w-full px-2 py-1 text-sm border border-blue-400 rounded bg-blue-50 focus:outline-none focus:border-blue-600 resize-none overflow-hidden leading-5"
        style={{ minHeight: "28px" }}
      />
      <div className="flex items-center gap-1.5 text-[11px] text-gray-400">
        <span className="mr-auto">
          <kbd className="rounded bg-gray-100 px-1">Esc</kbd> cancel
          {" · "}
          <kbd className="rounded bg-gray-100 px-1">Enter</kbd> save
        </span>
        <button
          onClick={onCancel}
          disabled={editLoading}
          className="p-0.5 rounded hover:bg-red-50 transition disabled:opacity-50"
          type="button"
          aria-label="Cancel edit"
        >
          <X size={14} className="text-red-500" />
        </button>
        <button
          onClick={onSubmit}
          disabled={editLoading}
          className="p-0.5 rounded hover:bg-green-50 transition disabled:opacity-50"
          type="button"
          aria-label="Save edit"
        >
          <Check size={14} className="text-green-600" />
        </button>
      </div>
      {editError && (
        <div className="text-xs text-red-600">{editError}</div>
      )}
    </div>
  );
}

function MessageContent({
  content,
  editedAt,
  replyPreview,
  blocks,
  forwardedFromMessageId,
  resolveMentionLabel,
  resolveMentionUserId,
  onJumpToMessage,
}: {
  content: string;
  editedAt: string | null;
  replyPreview: {
    senderId: string;
    senderAvatar: string | null;
    repliedToName: string;
    previewText: string;
    kind: "text" | "media" | "missing" | "unloaded";
    isOwnTarget: boolean;
    isMissingOriginal: boolean;
    repliedMessageId: string | null;
  } | null;
  blocks: MessageBlock[];
  forwardedFromMessageId: string | null;
  resolveMentionLabel: (token: string) => string;
  resolveMentionUserId: (token: string) => string | null;
  onJumpToMessage?: (messageId: string) => void;
}) {
  const renderableBlocks = getRenderableBlocks(blocks);
  const hasRenderableBlocks = renderableBlocks.length > 0;
  const isSingleTextBlock =
    renderableBlocks.length === 1 && renderableBlocks[0].type === "TEXT";
  const inlineTextContent = isSingleTextBlock
    ? renderableBlocks[0].text ?? content
    : content;

  return (
    <div className="flex flex-col gap-0.5">
      {forwardedFromMessageId && (
        <div className="w-fit rounded bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-700 inline-flex items-center gap-1">
          <Forward size={11} />
          <span className="italic">Forwarded</span>
        </div>
      )}
      {replyPreview && (
          <button
            data-testid="inline-reply-preview"
            type="button"
            disabled={replyPreview.isMissingOriginal || !replyPreview.repliedMessageId}
            onClick={() => {
              if (!replyPreview.repliedMessageId) return;
              onJumpToMessage?.(replyPreview.repliedMessageId);
            }}
            className={`flex items-center gap-1.5 rounded-r text-xs w-fit max-w-full border-l-2 pl-2 pr-2 py-0.5 ${
              replyPreview.isOwnTarget && !replyPreview.isMissingOriginal
                ? "border-l-amber-400 bg-amber-50/70 text-amber-800"
                : replyPreview.isMissingOriginal
                  ? "border-l-red-300 bg-red-50/60 text-red-700 cursor-default"
                  : replyPreview.kind === "unloaded"
                    ? "border-l-sky-300 bg-sky-50/60 text-sky-700 hover:bg-sky-100/70"
                  : "border-l-gray-300 bg-gray-100/60 text-gray-600 hover:bg-gray-200/60"
            }`}
            title={
              replyPreview.isMissingOriginal
                ? "Original message unavailable"
                : replyPreview.kind === "unloaded"
                  ? "Load original context"
                  : "Jump to original message"
            }
          >
            <CornerUpLeft size={11} className="opacity-50 flex-shrink-0" />
            <span className="text-[10px] opacity-60 mr-0.5">Replying to</span>
            <span className="font-semibold text-[11px]">{replyPreview.repliedToName}</span>
            {replyPreview.kind !== "missing" && (
              <>
                <span className="opacity-40 mx-0.5">·</span>
                <span className="truncate max-w-36 text-[11px]">{replyPreview.previewText}</span>
              </>
            )}
            {replyPreview.kind === "missing" && (
              <span className="truncate max-w-36 text-[11px] italic opacity-60">{replyPreview.previewText}</span>
            )}
          </button>
        )}
        {hasRenderableBlocks && !isSingleTextBlock ? (
          <div className="flex flex-col gap-0.5">
            <MessageBlocks
              blocks={blocks}
              resolveMentionLabel={resolveMentionLabel}
              resolveMentionUserId={resolveMentionUserId}
            />
            <EditedIndicator editedAt={editedAt} blocks={blocks} />
          </div>
        ) : (
          <div className="inline-block text-sm text-gray-800 whitespace-pre-wrap break-words">
            {renderContentWithMentions(
              inlineTextContent,
              resolveMentionLabel,
              resolveMentionUserId
            )}
            <EditedIndicator editedAt={editedAt} />
          </div>
        )}
      </div>
  );
}

function MessageActions({
  isOwnMessage,
  isEditing,
  forceVisible,
  canEdit,
  reactionLoading,
  onReply,
  onEdit,
  onDelete,
  onPin,
  onForward,
  onEmojiSelect,
  canUseMessageActions,
  isPinned,
}: {
  isOwnMessage: boolean;
  isEditing: boolean;
  forceVisible: boolean;
  canEdit: boolean;
  reactionLoading: boolean;
  onReply: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onPin: () => void;
  onForward: () => void;
  onEmojiSelect: (emoji: string) => void;
  canUseMessageActions: boolean;
  isPinned: boolean;
}) {
  return (
    <div
      className={`absolute -top-2 right-0 flex gap-1 transition bg-white rounded shadow-sm p-1 border border-gray-200 ${
        isEditing || forceVisible
          ? "opacity-100 z-20"
          : "opacity-0 group-hover:opacity-100"
      }`}
    >
      <button
        onClick={onReply}
        disabled={reactionLoading || isEditing || !canUseMessageActions}
        className="p-1.5 rounded hover:bg-gray-100 transition disabled:opacity-50"
        title="Reply"
        type="button"
      >
        <Reply size={14} className="text-gray-400 hover:text-gray-600" />
      </button>

      <button
        onClick={onPin}
        disabled={reactionLoading || isEditing || !canUseMessageActions}
        className="p-1.5 rounded hover:bg-gray-100 transition disabled:opacity-50"
        title={isPinned ? "Unpin message" : "Pin message"}
        type="button"
      >
        <Pin size={14} className={isPinned ? "text-blue-600" : "text-gray-400 hover:text-gray-600"} />
      </button>

      <button
        onClick={onForward}
        disabled={reactionLoading || isEditing || !canUseMessageActions}
        className="p-1.5 rounded hover:bg-gray-100 transition disabled:opacity-50"
        title="Forward message"
        type="button"
      >
        <Forward size={14} className="text-gray-400 hover:text-gray-600" />
      </button>

      {isOwnMessage && (
        <>
          {canEdit && !isEditing && (
            <button
              onClick={onEdit}
              disabled={reactionLoading || !canUseMessageActions}
              className="p-1.5 rounded hover:bg-gray-100 transition disabled:opacity-50"
              title="Edit message"
              type="button"
            >
              <Edit2 size={14} className="text-gray-400 hover:text-gray-600" />
            </button>
          )}

          <button
            onClick={onDelete}
            disabled={reactionLoading || isEditing || !canUseMessageActions}
            className="p-1.5 rounded hover:bg-gray-100 transition disabled:opacity-50"
            title="Delete message"
            type="button"
          >
            <Trash2 size={14} className="text-gray-400 hover:text-red-600" />
          </button>
        </>
      )}

      <EmojiPicker
        onEmojiSelect={onEmojiSelect}
        disabled={reactionLoading || isEditing || !canUseMessageActions}
        triggerClassName="p-1.5 text-gray-400 hover:text-gray-600"
      />
    </div>
  );
}
