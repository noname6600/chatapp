import {
  useEffect,
  useRef,
  useState,
  useCallback,
} from "react";
import type { ChatMessage } from "../../types/message";

import { useChat } from "../../store/chat.store";
import { useRooms } from "../../store/room.store";
import { useUserStore } from "../../store/user.store";
import { useNotifications } from "../../store/notification.store";
import { useDelete } from "../../hooks/useDelete";
import { useUnpin } from "../../hooks/useUnpin";
import { useNewestMessageVisibility } from "../../hooks/useNewestMessageVisibility";
import { deleteMessageApi, forwardMessage, pinMessage } from "../../api/chat.service";
import { isFeatureEnabled } from "../../config/featureFlags";
import { batchScrollToBottom, isAtBottom } from "../../utils/scrollUtils";
import MessageItem from "./MessageItem";
import ConfirmDeleteDialog from "./ConfirmDeleteDialog";
import ForwardMessageModal from "./ForwardMessageModal";
import SystemMessageItem from "./SystemMessageItem";
import { UnreadMessageIndicator } from "../message/UnreadMessageIndicator";
import { groupMessages } from "./messageGrouping";

interface Props {
  roomId: string;
  pinnedMessageIds?: Set<string>;
}

const CHAT_DEBUG = false;
const chatDebug = (...args: unknown[]) => {
  if (!CHAT_DEBUG) return;
  console.log("[CHAT_DEBUG][ui]", ...args);
};

const MAX_REPLY_JUMP_BACKFILL_ATTEMPTS = 6;
const TEMP_MESSAGE_PREFIX = "temp-";

const escapeRegex = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export default function MessageList({ roomId, pinnedMessageIds }: Props) {
  const {
    messagesByRoom,
    windowMetaByRoom = {},
    setActiveRoom,
    loadOlderMessages,
    loadNewerMessages = async () => {},
    loadMessagesAround,
    removeMessage,
    currentUserId,
    setMessageListContainerRef,
  } = useChat();
  const { roomsById, markRoomRead } = useRooms();
  const { fetchUsers } = useUserStore();
  const usersById = useUserStore((state) => state.users);
  const { clearRoomNotifications } = useNotifications();
  const { deletingMessageId, deletingContent, clearDeleting } = useDelete();
  const { setUnpinning } = useUnpin();
  const [deleteLoading, setDeleteLoading] = useState(false);

  const messages = messagesByRoom[roomId] || [];
  const roomUnreadCount = Math.max(0, roomsById[roomId]?.unreadCount ?? 0);
  const windowMeta = windowMetaByRoom[roomId];
  const latestSeq = windowMeta?.latestSeq ?? null;
  const newestSeq = windowMeta?.newestSeq ?? null;
  const oldestSeq = windowMeta?.oldestSeq ?? null;
  const hasNewerInWindow = windowMeta?.hasNewer ?? false;

  const effectiveLatestSeq =
    latestSeq != null && newestSeq != null
      ? Math.max(latestSeq, newestSeq)
      : (latestSeq ?? newestSeq);

  const firstUnreadSeq =
    roomUnreadCount > 0 && effectiveLatestSeq != null
      ? Math.max(1, effectiveLatestSeq - roomUnreadCount + 1)
      : null;
  const isBoundaryInWindow =
    firstUnreadSeq != null &&
    oldestSeq != null &&
    newestSeq != null &&
    firstUnreadSeq >= oldestSeq &&
    firstUnreadSeq <= newestSeq;
  // Guard against synthetic seq values (e.g. Number.MAX_SAFE_INTEGER from optimistic
  // placeholders) leaking into the behind-latest display label.
  const isValidBoundedSeq = (v: number | null | undefined): v is number =>
    v != null && Number.isFinite(v) && v < Number.MAX_SAFE_INTEGER;
  const distanceToLatest =
    hasNewerInWindow &&
    isValidBoundedSeq(effectiveLatestSeq) &&
    isValidBoundedSeq(newestSeq)
      ? Math.max(0, effectiveLatestSeq - newestSeq)
      : 0;
  const showDistanceIndicator = roomUnreadCount <= 0 && distanceToLatest >= 100;
  const showIncrementalIndicator =
    roomUnreadCount <= 0 && distanceToLatest > 0 && !showDistanceIndicator;

  const containerRef = useRef<HTMLDivElement>(null);
  const { isNewestVisible, evaluateVisibility } = useNewestMessageVisibility({
    containerRef,
    newestSeq,
  });

  const topIndicatorVisible =
    (roomUnreadCount > 0 && !isNewestVisible) || showDistanceIndicator || showIncrementalIndicator;

  const unreadDividerRef = useRef<HTMLDivElement>(null);

  const loadingRef = useRef(false);
  const loadingNewerRef = useRef(false);
  const prependingRef = useRef(false);
  const appendingRef = useRef(false);
  const prevHeightRef = useRef(0);
  const prevScrollTopRef = useRef(0);
  const prevHeightForAppendRef = useRef(0);
  const lastLoadAtRef = useRef(0);
  const lastRoomRef = useRef<string>("");
  const pinInFlightByMessageIdRef = useRef<Record<string, boolean>>({});
  const firstUnreadAnchorDoneRef = useRef<Record<string, boolean>>({});
  const initialBottomAnchorDoneRef = useRef<Record<string, boolean>>({});
  const markReadInFlightRef = useRef<Record<string, boolean>>({});
  const markReadSentRef = useRef<Record<string, boolean>>({});
  const lastMarkReadSeqRef = useRef<Record<string, number | null>>({});
  const autoPrefetchAttemptsRef = useRef<Record<string, number>>({});
  const autoPrefetchMessageCountRef = useRef<Record<string, number>>({});
  const prevMessageCountByRoomRef = useRef<Record<string, number>>({});
  const jumpResolverInFlightRef = useRef<Record<string, boolean>>({});

  const [loadingOld, setLoadingOld] = useState(false);
  const [loadingNew, setLoadingNew] = useState(false);
  const [jumpTargetMessageId, setJumpTargetMessageId] = useState<string | null>(null);
  const [forwardSourceMessage, setForwardSourceMessage] = useState<ChatMessage | null>(null);
  const [unavailableReplyTargetsByRoom, setUnavailableReplyTargetsByRoom] =
    useState<Record<string, Record<string, boolean>>>({});
  const wasPinnedToBottomRef = useRef(true);

  const handleConfirmDelete = useCallback(async (messageId: string) => {
    setDeleteLoading(true);
    try {
      // Failed optimistic placeholders use temporary IDs and are never persisted.
      // Remove them locally instead of calling delete API with invalid IDs.
      if (messageId.startsWith(TEMP_MESSAGE_PREFIX)) {
        removeMessage(messageId, roomId);
        return;
      }
      await deleteMessageApi(messageId);
      removeMessage(messageId, roomId);
    } catch (error) {
      console.error("Delete failed:", error);
    } finally {
      setDeleteLoading(false);
      clearDeleting();
    }
  }, [roomId, removeMessage, clearDeleting]);

  const handleCancelDelete = useCallback(() => {
    clearDeleting();
  }, [clearDeleting]);

  const handlePinMessage = useCallback(async (message: ChatMessage) => {
    if (pinInFlightByMessageIdRef.current[message.messageId]) {
      return;
    }

    const isPinned = pinnedMessageIds?.has(message.messageId) ?? false;
    if (isPinned) {
      setUnpinning(
        message.messageId,
        (message.content || "").trim() || "(attachment or structured content)"
      );
      return;
    }

    pinInFlightByMessageIdRef.current[message.messageId] = true;
    try {
      await pinMessage(roomId, message.messageId);
    } catch (error) {
      console.error("Pin failed:", error);
    } finally {
      pinInFlightByMessageIdRef.current[message.messageId] = false;
    }
  }, [pinnedMessageIds, roomId, setUnpinning]);

  const handleForwardConfirm = useCallback(async (targetRoomId: string) => {
    if (!forwardSourceMessage) return;

    try {
      await forwardMessage(forwardSourceMessage.messageId, targetRoomId);
    } catch (error) {
      console.error("Forward failed:", error);
    }
  }, [forwardSourceMessage]);

  const groupedMessages = groupMessages(messages);

  const messageById = new Map(messages.map((message) => [message.messageId, message]));
  const linkedHighlightMessageIds = new Set<string>();
  const mentionedHighlightMessageIds = new Set<string>();
  const currentUsername = currentUserId
    ? usersById[currentUserId]?.username?.trim().toLowerCase() || null
    : null;
  const currentUsernamePattern = currentUsername
    ? new RegExp(`(^|\\s)@${escapeRegex(currentUsername)}(?=$|\\s|[.,!?;:])`, "i")
    : null;

  messages.forEach((message) => {
    if (message.replyToMessageId) {
      const original = messageById.get(message.replyToMessageId);
      if (original && original.senderId === currentUserId) {
        // Only highlight the reply message (the new message), not the original
        linkedHighlightMessageIds.add(message.messageId);
      }
    }

    const explicitMentions = message.mentionedUserIds ?? [];
    const hasExplicitSelfMention =
      Boolean(currentUserId) && explicitMentions.includes(currentUserId as string);

    const hasTextSelfMention =
      Boolean(currentUsernamePattern) &&
      typeof message.content === "string" &&
      currentUsernamePattern?.test(message.content);

    if (hasExplicitSelfMention || hasTextSelfMention) {
      mentionedHighlightMessageIds.add(message.messageId);
    }
  });

  const waitForDomCommit = useCallback(
    () =>
      new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
      ),
    []
  );

  const findMessageElement = useCallback((messageId: string) => {
    const inContainer = containerRef.current?.querySelector<HTMLElement>(
      `[data-message-id="${messageId}"]`
    );
    return inContainer ?? document.querySelector<HTMLElement>(`[data-message-id="${messageId}"]`);
  }, []);

  const clearReplyTargetUnavailable = useCallback((messageId: string) => {
    setUnavailableReplyTargetsByRoom((prev) => {
      const roomMap = prev[roomId];
      if (!roomMap?.[messageId]) return prev;

      const nextRoomMap = { ...roomMap };
      delete nextRoomMap[messageId];

      return {
        ...prev,
        [roomId]: nextRoomMap,
      };
    });
  }, [roomId]);

  const markReplyTargetUnavailable = useCallback((messageId: string) => {
    setUnavailableReplyTargetsByRoom((prev) => ({
      ...prev,
      [roomId]: {
        ...(prev[roomId] ?? {}),
        [messageId]: true,
      },
    }));
  }, [roomId]);

  const markJumpTarget = useCallback((messageId: string) => {
    setJumpTargetMessageId(messageId);
    window.setTimeout(() => {
      setJumpTargetMessageId((current) => (current === messageId ? null : current));
    }, 1500);
  }, []);

  const handleJumpToMessage = useCallback(async (messageId: string) => {
    const inFlightKey = `${roomId}:${messageId}`;
    if (jumpResolverInFlightRef.current[inFlightKey]) {
      return;
    }

    jumpResolverInFlightRef.current[inFlightKey] = true;
    clearReplyTargetUnavailable(messageId);

    const initialScrollTop = containerRef.current?.scrollTop ?? null;

    try {
      let target = findMessageElement(messageId);
      if (target) {
        target.scrollIntoView({ behavior: "smooth", block: "center" });
        markJumpTarget(messageId);
        return;
      }

      try {
        await loadMessagesAround(roomId, messageId);
      } catch {
        // Continue into bounded backfill path.
      }

      await waitForDomCommit();
      target = findMessageElement(messageId);
      if (target) {
        target.scrollIntoView({ behavior: "smooth", block: "center" });
        markJumpTarget(messageId);
        return;
      }

      let attempts = 0;
      while (attempts < MAX_REPLY_JUMP_BACKFILL_ATTEMPTS) {
        const hasOlder = windowMetaByRoom[roomId]?.hasOlder ?? false;
        if (!hasOlder) break;

        const beforeCount = messagesByRoom[roomId]?.length ?? 0;

        const el = containerRef.current;
        const prevHeight = el?.scrollHeight ?? 0;
        const prevScrollTop = el?.scrollTop ?? 0;

        await loadOlderMessages(roomId);
        await waitForDomCommit();

        if (el) {
          const newHeight = el.scrollHeight;
          const diff = newHeight - prevHeight;
          el.scrollTop = prevScrollTop + diff;
        }

        target = findMessageElement(messageId);
        if (target) {
          target.scrollIntoView({ behavior: "smooth", block: "center" });
          markJumpTarget(messageId);
          return;
        }

        const afterCount = messagesByRoom[roomId]?.length ?? 0;
        if (afterCount <= beforeCount) break;

        attempts += 1;
      }

      if (initialScrollTop != null && containerRef.current) {
        containerRef.current.scrollTop = initialScrollTop;
      }

      markReplyTargetUnavailable(messageId);
    } finally {
      jumpResolverInFlightRef.current[inFlightKey] = false;
    }
  }, [
    clearReplyTargetUnavailable,
    findMessageElement,
    loadMessagesAround,
    markJumpTarget,
    markReplyTargetUnavailable,
    loadOlderMessages,
    messagesByRoom,
    roomId,
    waitForDomCommit,
    windowMetaByRoom,
  ]);

  useEffect(() => {
    const handler = (event: Event) => {
      const customEvent = event as CustomEvent<{ roomId: string; messageId: string }>;
      if (!customEvent.detail) return;
      if (customEvent.detail.roomId !== roomId) return;
      void handleJumpToMessage(customEvent.detail.messageId);
    };

    window.addEventListener("chat:jump-to-message", handler as EventListener);
    return () => {
      window.removeEventListener("chat:jump-to-message", handler as EventListener);
    };
  }, [handleJumpToMessage, roomId]);

  // Mark-read policy: trigger from meaningful interactions (boundary crossing,
  // jump-to-latest, and confirmed live-tail view), deduped per room-view cycle.
  const triggerMarkRead = useCallback(async () => {
    if (!roomId || roomUnreadCount <= 0) return;
    if (markReadSentRef.current[roomId]) return;
    if (markReadInFlightRef.current[roomId]) return;

    const readSeq = effectiveLatestSeq;
    if (
      readSeq != null &&
      lastMarkReadSeqRef.current[roomId] != null &&
      lastMarkReadSeqRef.current[roomId] === readSeq
    ) {
      return;
    }

    markReadInFlightRef.current[roomId] = true;
    try {
      await markRoomRead(roomId);
      markReadSentRef.current[roomId] = true;
      lastMarkReadSeqRef.current[roomId] = readSeq ?? null;
      // Bridge: clear room notifications on the notification service so that
      // action-required entries remain but passive message notifications are
      // marked read, keeping the notification count in sync with the chat read.
      void clearRoomNotifications(roomId).catch(() => {});
    } finally {
      markReadInFlightRef.current[roomId] = false;
    }
  }, [clearRoomNotifications, effectiveLatestSeq, markRoomRead, roomId, roomUnreadCount]);

  const handleJumpToLatest = useCallback(async () => {
    if (!roomId) return;

    const attempts = 8;
    for (let i = 0; i < attempts; i += 1) {
      const meta = windowMetaByRoom[roomId];
      if (!meta?.hasNewer) break;
      await loadNewerMessages(roomId);
      await new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
      );
    }

    const el = containerRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }

    await triggerMarkRead();
  }, [loadNewerMessages, roomId, triggerMarkRead, windowMetaByRoom]);

  // ================= CONTAINER REF REGISTRATION FOR AUTO-SCROLL =================
  useEffect(() => {
    setMessageListContainerRef(containerRef.current);
    return () => {
      setMessageListContainerRef(null);
    };
  }, [setMessageListContainerRef]);

  // ================= INIT =================
  useEffect(() => {
    if (!roomId) return;

    if (lastRoomRef.current !== roomId) {
      lastRoomRef.current = roomId;
      lastLoadAtRef.current = 0;
      firstUnreadAnchorDoneRef.current[roomId] = false;
      initialBottomAnchorDoneRef.current[roomId] = false;
      markReadSentRef.current[roomId] = false;
      lastMarkReadSeqRef.current[roomId] = null;
      prevMessageCountByRoomRef.current[roomId] = 0;

      if (typeof setActiveRoom === "function") {
        chatDebug("room:init:setActiveRoom", { roomId, roomUnreadCount });
        void setActiveRoom(roomId, roomUnreadCount);
      }
    }

    autoPrefetchAttemptsRef.current[roomId] = 0;
    autoPrefetchMessageCountRef.current[roomId] = 0;
  }, [roomId, roomUnreadCount, setActiveRoom]);

  useEffect(() => {
    if (!roomId) return;
    if (roomUnreadCount <= 0) {
      markReadSentRef.current[roomId] = false;
      return;
    }
    markReadSentRef.current[roomId] = false;
  }, [roomId, roomUnreadCount]);

  useEffect(() => {
    const ids = [...new Set(messages.map((m) => m.senderId))];
    if (ids.length) {
      void fetchUsers(ids);
    }
  }, [messages, fetchUsers]);

  useEffect(() => {
    evaluateVisibility();
  }, [evaluateVisibility, messages.length, newestSeq, roomId]);

  useEffect(() => {
    const roomMap = unavailableReplyTargetsByRoom[roomId];
    if (!roomMap) return;

    const loadedIds = new Set(messages.map((m) => m.messageId));
    const staleIds = Object.keys(roomMap).filter((id) => loadedIds.has(id));
    if (!staleIds.length) return;

    setUnavailableReplyTargetsByRoom((prev) => {
      const currentRoomMap = prev[roomId];
      if (!currentRoomMap) return prev;

      const nextRoomMap = { ...currentRoomMap };
      staleIds.forEach((id) => {
        delete nextRoomMap[id];
      });

      return {
        ...prev,
        [roomId]: nextRoomMap,
      };
    });
  }, [messages, roomId, unavailableReplyTargetsByRoom]);

  // ================= SCROLL BOTTOM INIT =================
  useEffect(() => {
    const el = containerRef.current;
    if (!el || prependingRef.current) return;
    if (roomUnreadCount > 0) return;
    if (!messages.length) return;

    const prevCount = prevMessageCountByRoomRef.current[roomId] ?? 0;
    const countIncreased = messages.length > prevCount;
    prevMessageCountByRoomRef.current[roomId] = messages.length;

    requestAnimationFrame(() => {
      // On first load of a room with no unread, force latest-position anchor.
      if (!initialBottomAnchorDoneRef.current[roomId]) {
        chatDebug("anchor:bottom:first", {
          roomId,
          messages: messages.length,
          scrollTop: el.scrollTop,
          scrollHeight: el.scrollHeight,
          clientHeight: el.clientHeight,
        });
        el.scrollTop = el.scrollHeight;
        initialBottomAnchorDoneRef.current[roomId] = true;
        return;
      }

      // Only auto-scroll when a genuinely new message was added (count increased).
      // Same-count updates are optimistic→confirmed reconciliations — no scroll needed.
      if (!countIncreased) return;

      // After first load, keep bottom anchored if user was already pinned,
      // or if they just sent the newest message.
      const newestMessage = messages[messages.length - 1];
      const newestIsFromCurrentUser =
        Boolean(currentUserId) && newestMessage?.senderId === currentUserId;
      const nearBottom = isAtBottom(el);
      const shouldStickToBottom =
        (isFeatureEnabled("enableAutoScrollOnNewMessage") && (wasPinnedToBottomRef.current || newestIsFromCurrentUser)) ||
        (!isFeatureEnabled("enableAutoScrollOnNewMessage") && nearBottom);

      chatDebug("anchor:bottom:update", {
        roomId,
        messages: messages.length,
        nearBottom,
        wasPinnedToBottom: wasPinnedToBottomRef.current,
        newestIsFromCurrentUser,
        scrollTop: el.scrollTop,
        scrollHeight: el.scrollHeight,
        clientHeight: el.clientHeight,
      });

      if (shouldStickToBottom) {
        if (isFeatureEnabled("enableAutoScrollOnNewMessage")) {
          batchScrollToBottom(el);
        } else {
          el.scrollTop = el.scrollHeight;
        }
      }
    });
  }, [messages, roomUnreadCount, roomId, currentUserId]);

  useEffect(() => {
    if (!roomId || !isBoundaryInWindow || firstUnreadSeq == null) return;
    if (firstUnreadAnchorDoneRef.current[roomId]) return;

    // Use requestAnimationFrame to ensure DOM is ready before scrolling
    const raf = requestAnimationFrame(() => {
      const target = document.querySelector<HTMLElement>(`[data-message-seq="${firstUnreadSeq}"]`);
      if (target) {
        chatDebug("anchor:unread", {
          roomId,
          firstUnreadSeq,
          messages: messages.length,
        });
        target.scrollIntoView({ block: "center" });
        firstUnreadAnchorDoneRef.current[roomId] = true;
      }
    });

    return () => cancelAnimationFrame(raf);
  }, [firstUnreadSeq, isBoundaryInWindow, roomId, messages.length]);

  // ================= LOAD OLDER =================
  const triggerLoadOlder = useCallback(async (options?: { stickToBottom?: boolean }) => {
    const el = containerRef.current;
    if (!el || loadingRef.current) return;
    if (!messages.length) return;

    const stickToBottom = options?.stickToBottom ?? false;

    chatDebug("loadOlder:trigger", {
      roomId,
      messages: messages.length,
      stickToBottom,
    });

    loadingRef.current = true;
    prependingRef.current = true;
    setLoadingOld(true);
    lastLoadAtRef.current = Date.now();

    prevHeightRef.current = el.scrollHeight;
    prevScrollTopRef.current = el.scrollTop;

    try {
      await loadOlderMessages(roomId);

      requestAnimationFrame(() => {
        if (stickToBottom) {
          chatDebug("loadOlder:after:stickBottom", {
            roomId,
            scrollTop: el.scrollTop,
            scrollHeight: el.scrollHeight,
            clientHeight: el.clientHeight,
          });
          el.scrollTop = el.scrollHeight;
          return;
        }

        const newHeight = el.scrollHeight;
        const diff = newHeight - prevHeightRef.current;

        chatDebug("loadOlder:after:preserve", {
          roomId,
          oldHeight: prevHeightRef.current,
          newHeight,
          diff,
          oldScrollTop: prevScrollTopRef.current,
          newScrollTop: prevScrollTopRef.current + diff,
        });

        el.scrollTop = prevScrollTopRef.current + diff;
      });
    } finally {
      loadingRef.current = false;
      setLoadingOld(false);

      setTimeout(() => {
        prependingRef.current = false;
      }, 50);
    }
  }, [roomId, loadOlderMessages, messages.length]);

  const triggerLoadNewer = useCallback(async () => {
    const el = containerRef.current;
    if (!el || loadingNewerRef.current) return;
    if (!windowMetaByRoom[roomId]?.hasNewer) return;

    loadingNewerRef.current = true;
    appendingRef.current = true;
    setLoadingNew(true);
    prevHeightForAppendRef.current = el.scrollHeight;

    await loadNewerMessages(roomId);

    requestAnimationFrame(() => {
      const heightDelta = el.scrollHeight - prevHeightForAppendRef.current;
      if (heightDelta > 0) {
        el.scrollTop += heightDelta;
      }
    });

    loadingNewerRef.current = false;
    setLoadingNew(false);

    setTimeout(() => {
      appendingRef.current = false;
    }, 50);
  }, [loadNewerMessages, roomId, windowMetaByRoom]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el || !messages.length || loadingRef.current) return;

    const canScroll = el.scrollHeight > el.clientHeight + 1;
    if (canScroll) {
      autoPrefetchAttemptsRef.current[roomId] = 0;
      autoPrefetchMessageCountRef.current[roomId] = messages.length;
      return;
    }

    const attempts = autoPrefetchAttemptsRef.current[roomId] ?? 0;
    const previousCount = autoPrefetchMessageCountRef.current[roomId] ?? 0;

    // Keep auto-prefetch bounded to avoid loading entire history at room-open,
    // while still allowing enough pages to make the list scrollable.
    if (attempts >= 3 || messages.length >= 150) return;
    if (attempts > 0 && messages.length <= previousCount) return;

    autoPrefetchAttemptsRef.current[roomId] = attempts + 1;
    autoPrefetchMessageCountRef.current[roomId] = messages.length;

    chatDebug("autoPrefetch:loadOlder", {
      roomId,
      attempts: attempts + 1,
      messageCount: messages.length,
      roomUnreadCount,
      canScroll,
    });

    void triggerLoadOlder({ stickToBottom: roomUnreadCount <= 0 });
  }, [messages.length, roomId, roomUnreadCount, triggerLoadOlder]);

  useEffect(() => {
    if (!roomId) return;
    if (roomUnreadCount <= 0) return;
    if (!isNewestVisible) return;
    if (hasNewerInWindow) return;

    void triggerMarkRead();
  }, [hasNewerInWindow, isNewestVisible, roomId, roomUnreadCount, triggerMarkRead]);

  // ================= SCROLL =================
  const handleScroll = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;

    const cooldownPassed = Date.now() - lastLoadAtRef.current > 650;

    if (el.scrollTop <= 120 && !loadingRef.current && cooldownPassed) {
      chatDebug("scroll:top:loadOlder", {
        roomId,
        scrollTop: el.scrollTop,
        cooldownPassed,
      });
      void triggerLoadOlder();
    }

    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= 120;
    wasPinnedToBottomRef.current = nearBottom;
    if (nearBottom && !loadingNewerRef.current && (windowMetaByRoom[roomId]?.hasNewer ?? false)) {
      void triggerLoadNewer();
    }

    const divider = unreadDividerRef.current;
    if (divider && roomUnreadCount > 0) {
      const boundaryVisible = el.scrollTop + el.clientHeight * 0.45 >= divider.offsetTop;
      if (boundaryVisible) {
        void triggerMarkRead();
      }
    }

    if (nearBottom && roomUnreadCount > 0 && !(windowMetaByRoom[roomId]?.hasNewer ?? false)) {
      void triggerMarkRead();
    }
  }, [roomId, roomUnreadCount, triggerLoadOlder, triggerLoadNewer, triggerMarkRead, windowMetaByRoom]);

  // ================= RENDER =================
  return (
    <div className="flex h-full flex-col min-h-0 bg-white">
      {topIndicatorVisible && (
        <UnreadMessageIndicator
          unreadCount={roomUnreadCount}
          onJumpToLatest={handleJumpToLatest}
          label={
            showDistanceIndicator
              ? `${distanceToLatest}+ messages behind latest`
              : showIncrementalIndicator
                ? (distanceToLatest === 1 ? "1 new message" : `${distanceToLatest} new messages`)
                : undefined
          }
          jumpLabel={showDistanceIndicator ? "Jump to Newest" : "Jump to Latest"}
        />
      )}
      <div
        ref={containerRef}
        onScroll={handleScroll}
        className="flex-1 min-h-0 overflow-y-auto px-4 py-3"
      >
        {loadingOld && (
          <div className="text-xs text-gray-400 text-center py-2">
            Loading...
          </div>
        )}

        {loadingNew && (
          <div className="text-xs text-blue-500 text-center py-2">
            Loading newer messages...
          </div>
        )}

        {groupedMessages.map((group) =>
          group.messages.map((m, indexInGroup) => (
            <div key={m.clientMessageId ?? m.messageId} data-message-seq={m.seq}>
              {isBoundaryInWindow && firstUnreadSeq === m.seq && (
                <div
                  ref={unreadDividerRef}
                  className="my-3 flex items-center gap-3"
                  role="separator"
                  aria-label="Unread boundary"
                >
                  <div className="h-px flex-1 bg-red-300" />
                  <span className="text-xs font-semibold uppercase tracking-wide text-red-600">
                    Unread messages
                  </span>
                  <div className="h-px flex-1 bg-red-300" />
                </div>
              )}

              {m.type === "SYSTEM" ? (
                <SystemMessageItem
                  message={m}
                  onJumpToMessage={handleJumpToMessage}
                  onOpenPinnedMessages={() => {
                    window.dispatchEvent(
                      new CustomEvent("chat:open-pins-panel", { detail: { roomId } })
                    );
                  }}
                />
              ) : (
                <MessageItem
                  message={m}
                  showUserInfo={indexInGroup === 0}
                  isGrouped={group.messages.length > 1}
                  indexInGroup={indexInGroup}
                  repliedMessageId={m.replyToMessageId}
                  isReplyTargetUnavailable={
                    Boolean(
                      m.replyToMessageId &&
                      unavailableReplyTargetsByRoom[roomId]?.[m.replyToMessageId]
                    )
                  }
                  repliedMessage={
                    m.replyToMessageId
                      ? messageById.get(m.replyToMessageId) ?? null
                      : null
                  }
                  onJumpToMessage={handleJumpToMessage}
                  isReplyLinkedHighlight={
                    linkedHighlightMessageIds.has(m.messageId) ||
                    mentionedHighlightMessageIds.has(m.messageId)
                  }
                  isJumpTarget={jumpTargetMessageId === m.messageId}
                  onPin={handlePinMessage}
                  onForward={setForwardSourceMessage}
                  isPinned={pinnedMessageIds?.has(m.messageId) ?? false}
                  onShowDeleteDialog={() => { /* state already set by MessageItem via useDelete */ }}
                />
              )}
            </div>
          ))
        )}
      </div>

      <ForwardMessageModal
        open={Boolean(forwardSourceMessage)}
        sourceMessage={forwardSourceMessage}
        currentRoomId={roomId}
        rooms={Object.values(roomsById)}
        users={usersById}
        onClose={() => setForwardSourceMessage(null)}
        onConfirm={handleForwardConfirm}
      />

      {deletingMessageId && deletingContent != null && (
        <ConfirmDeleteDialog
          messageId={deletingMessageId}
          messagePreview={deletingContent}
          onConfirm={handleConfirmDelete}
          onCancel={handleCancelDelete}
          loading={deleteLoading}
        />
      )}
    </div>
  );
}

