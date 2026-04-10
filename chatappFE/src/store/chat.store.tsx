import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  useCallback,
} from "react";
import type { ReactNode } from "react";

import type { ChatMessage, Attachment, MessageBlock } from "../types/message";
import {
  getLatestMessages,
  getMessagesBefore,
  getMessageRange,
  sendMessageApi,
  getMessagesAround,
} from "../api/chat.service";

import {
  onChatEvent,
  onSocketOpen,
  subscribeRoom,
} from "../websocket/chat.socket";
import { ChatEventType } from "../constants/chatEvents";

import { getMyProfileApi } from "../api/user.service";
import { isFeatureEnabled } from "../config/featureFlags";
import { isAtBottom, batchScrollToBottom } from "../utils/scrollUtils";

const MAX_WINDOW = 500;
const OPTIMISTIC_CONFIRM_TIMEOUT_MS = 15_000;

const logSendFlow = (event: string, payload: Record<string, unknown>) => {
  if (!import.meta.env.DEV) return;
  if (import.meta.env.MODE === "test") return;
  console.info("[send-flow]", { event, ...payload });
};

interface ChatContextType {
  messagesByRoom: Record<string, ChatMessage[]>;
  windowMetaByRoom: Record<string, {
    oldestSeq: number | null;
    newestSeq: number | null;
    latestSeq: number | null;
    hasOlder: boolean;
    hasNewer: boolean;
  }>;
  activeRoomId: string | null;
  currentUserId: string | null;
  replyingTo: ChatMessage | null;
  setReplyingTo: (msg: ChatMessage | null) => void;

  setActiveRoom: (roomId: string, unreadCount?: number) => Promise<void>;
  loadOlderMessages: (roomId: string) => Promise<void>;
  loadNewerMessages: (roomId: string) => Promise<void>;
  loadMessagesAround: (roomId: string, messageId: string) => Promise<void>;
  upsertMessage: (msg: ChatMessage) => void;
  removeMessage: (messageId: string, roomId: string) => void;
  setMessageListContainerRef: (ref: HTMLDivElement | null) => void;

  sendMessage: (
    roomId: string,
    content: string,
    attachments?: Attachment[],
    replyToMessageId?: string | null,
    blocks?: MessageBlock[],
    mentionedUserIds?: string[]
  ) => Promise<void>;
  retryMessage: (roomId: string, messageId: string) => Promise<void>;
}

const ChatContext = createContext<ChatContextType | undefined>(undefined);

export function ChatProvider({ children }: { children: ReactNode }) {
  const [messagesByRoom, setMessagesByRoom] =
    useState<Record<string, ChatMessage[]>>({});
    // Ref mirror kept in sync every render so stable callbacks can read current messages
    // without needing messagesByRoom in their useCallback dependency arrays.
    const messagesByRoomRef = useRef<Record<string, ChatMessage[]>>({});
    messagesByRoomRef.current = messagesByRoom;

  const messageListContainerRef = useRef<HTMLDivElement | null>(null);

  const [windowMetaByRoom, setWindowMetaByRoom] =
    useState<Record<string, {
      oldestSeq: number | null;
      newestSeq: number | null;
      latestSeq: number | null;
      hasOlder: boolean;
      hasNewer: boolean;
    }>>({});

  const [activeRoomId, setActiveRoomId] = useState<string | null>(null);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const [replyingTo, setReplyingTo] = useState<ChatMessage | null>(null);
  const activeRoomIdRef = useRef<string | null>(null);
  activeRoomIdRef.current = activeRoomId;

  // Refs for stable handler closures that never re-register
  const setActiveRoomRef = useRef<(roomId: string, unreadCount?: number) => Promise<void>>(async () => {});
  const upsertMessageRef = useRef<(msg: ChatMessage) => void>(() => {});

  const oldestSeqByRoom = useRef<Record<string, number | null>>({});
  const newestSeqByRoom = useRef<Record<string, number | null>>({});
  const latestSeqByRoom = useRef<Record<string, number | null>>({});
  const hasMoreByRoom = useRef<Record<string, boolean>>({});
  const hasNewerByRoom = useRef<Record<string, boolean>>({});
  const loadingByRoom = useRef<Record<string, boolean>>({});
  const loadingNewerByRoom = useRef<Record<string, boolean>>({});
  const activatingByRoom = useRef<Record<string, boolean>>({});
  const subscribedRooms = useRef(new Set<string>());
  const sendPayloadByClientMessageId = useRef<
    Record<
      string,
      {
        roomId: string;
        content: string;
        attachments: Attachment[];
        replyToMessageId?: string | null;
        blocks: MessageBlock[];
        mentionedUserIds: string[];
      }
    >
  >({});
  const pendingTimeoutByClientMessageId = useRef<Record<string, number>>({});

  const logChatTelemetry = useCallback((event: string, payload: Record<string, unknown>) => {
    if (!import.meta.env.DEV) return;
    if (import.meta.env.MODE === "test") return;
    console.info("[chat-telemetry]", { event, ...payload });
  }, []);

  const clearPendingTimeout = useCallback((clientMessageId: string | null | undefined) => {
    if (!clientMessageId) return;
    const timer = pendingTimeoutByClientMessageId.current[clientMessageId];
    if (!timer) return;
    clearTimeout(timer);
    delete pendingTimeoutByClientMessageId.current[clientMessageId];
  }, []);

  const updateDeliveryStatusByClientMessageId = useCallback(
    (roomId: string, clientMessageId: string, status: "pending" | "failed" | "sent") => {
      setMessagesByRoom((prev) => {
        const current = prev[roomId] || [];
        let changed = false;

        const next = current.map((m) => {
          if (m.clientMessageId !== clientMessageId) return m;
          if (m.deliveryStatus === status) return m;
          changed = true;
          return {
            ...m,
            deliveryStatus: status,
          };
        });

        if (!changed) return prev;
        return {
          ...prev,
          [roomId]: next,
        };
      });
    },
    []
  );

  const schedulePendingTimeout = useCallback(
    (roomId: string, clientMessageId: string) => {
      clearPendingTimeout(clientMessageId);

      pendingTimeoutByClientMessageId.current[clientMessageId] = window.setTimeout(() => {
        delete pendingTimeoutByClientMessageId.current[clientMessageId];
        updateDeliveryStatusByClientMessageId(roomId, clientMessageId, "failed");
        logChatTelemetry("send_timeout", { roomId, clientMessageId });
      }, OPTIMISTIC_CONFIRM_TIMEOUT_MS);
    },
    [clearPendingTimeout, updateDeliveryStatusByClientMessageId, logChatTelemetry]
  );

  const updateWindowMeta = useCallback((roomId: string) => {
    const oldestSeq = oldestSeqByRoom.current[roomId] ?? null;
    const newestSeq = newestSeqByRoom.current[roomId] ?? null;
    const latestSeq = latestSeqByRoom.current[roomId] ?? newestSeq;
    const hasOlder = hasMoreByRoom.current[roomId] ?? false;
    const hasNewer = hasNewerByRoom.current[roomId] ?? false;

    setWindowMetaByRoom((prev) => ({
      ...prev,
      [roomId]: {
        oldestSeq,
        newestSeq,
        latestSeq,
        hasOlder,
        hasNewer,
      },
    }));
  }, []);

  // ================= USER =================
  useEffect(() => {
    getMyProfileApi()
      .then((u) => setCurrentUserId(u.accountId))
      .catch(() => {});
  }, []);

  // ================= UPSERT =================
  const upsertMessage = useCallback((msg: ChatMessage) => {
    const isOptimisticTemp = msg.messageId.startsWith("temp-");

    logSendFlow("upsert_incoming", {
      roomId: msg.roomId,
      messageId: msg.messageId,
      seq: msg.seq,
      clientMessageId: msg.clientMessageId ?? null,
      isOptimisticTemp,
    });

    if (msg.clientMessageId && !isOptimisticTemp) {
      clearPendingTimeout(msg.clientMessageId);
      delete sendPayloadByClientMessageId.current[msg.clientMessageId];
    }

    setMessagesByRoom((prev) => {
      const current = prev[msg.roomId] || [];

      const map = new Map(current.map((m) => [m.messageId, m]));

      // Reconcile optimistic temp message with server message using clientMessageId.
      if (msg.clientMessageId) {
        const optimisticMatch = current.find(
          (m) =>
            m.clientMessageId === msg.clientMessageId &&
            m.messageId !== msg.messageId
        );

        if (optimisticMatch) {
          logSendFlow("upsert_reconcile_optimistic", {
            roomId: msg.roomId,
            optimisticMessageId: optimisticMatch.messageId,
            serverMessageId: msg.messageId,
            clientMessageId: msg.clientMessageId,
          });
          map.delete(optimisticMatch.messageId);
        }
      }

      map.set(
        msg.messageId,
        isOptimisticTemp
          ? msg
          : {
              ...msg,
              deliveryStatus: "sent",
            }
      );

      const next = Array.from(map.values()).sort((a, b) => a.seq - b.seq);
      const trimmed = next.length > MAX_WINDOW ? next.slice(-MAX_WINDOW) : next;

      oldestSeqByRoom.current[msg.roomId] = trimmed[0]?.seq ?? null;
      newestSeqByRoom.current[msg.roomId] = trimmed.at(-1)?.seq ?? null;

      // Only server-confirmed messages are authoritative for latestSeq.
      // Optimistic placeholders use Number.MAX_SAFE_INTEGER as a temp ordering
      // sentinel and must not poison the behind-latest or unread calculations.
      if (!isOptimisticTemp) {
        const incomingLatest = msg.seq ?? null;
        const currentLatest = latestSeqByRoom.current[msg.roomId] ?? null;
        latestSeqByRoom.current[msg.roomId] =
          currentLatest == null || (incomingLatest != null && incomingLatest > currentLatest)
            ? incomingLatest
            : currentLatest;
      }

      const newest = newestSeqByRoom.current[msg.roomId];
      const latest = latestSeqByRoom.current[msg.roomId];
      hasNewerByRoom.current[msg.roomId] =
        newest != null && latest != null ? newest < latest : false;

      // Keep metadata reactive for UI decisions (divider/indicators/load direction).
      updateWindowMeta(msg.roomId);

      const last = trimmed.at(-1);
      logSendFlow("upsert_applied", {
        roomId: msg.roomId,
        count: trimmed.length,
        lastMessageId: last?.messageId ?? null,
        lastSeq: last?.seq ?? null,
        lastClientMessageId: last?.clientMessageId ?? null,
      });

      return {
        ...prev,
        [msg.roomId]: trimmed,
      };
    });
  }, [clearPendingTimeout, updateWindowMeta]);

  // ================= SET ROOM =================
  const setActiveRoom = useCallback(async (roomId: string, unreadCount = 0) => {
    if (!roomId) return;
    if (activatingByRoom.current[roomId]) {
      return;
    }
    activatingByRoom.current[roomId] = true;

    try {
      setActiveRoomId(roomId);

      if (!subscribedRooms.current.has(roomId)) {
        subscribeRoom(roomId);
        subscribedRooms.current.add(roomId);
      }

      const page = await getLatestMessages(roomId);

      const latestFromApi = page.messages.at(-1);
      logSendFlow("latest_loaded", {
        roomId,
        count: page.messages.length,
        hasMore: page.hasMore,
        lastMessageId: latestFromApi?.messageId ?? null,
        lastSeq: latestFromApi?.seq ?? null,
        lastClientMessageId: latestFromApi?.clientMessageId ?? null,
      });

      const latestWindow = page.messages.sort((a, b) => a.seq - b.seq);
      const latestSeq = latestWindow.at(-1)?.seq ?? null;

      latestSeqByRoom.current[roomId] = latestSeq;

      let windowMessages = latestWindow;

    // Anchor entry near first unread when boundary is outside current latest window.
      if (unreadCount > 0 && latestSeq != null && latestWindow.length > 0) {
        const firstUnreadSeq = Math.max(1, latestSeq - unreadCount + 1);
        const topSeq = latestWindow[0].seq;

        if (firstUnreadSeq < topSeq) {
          const startSeq = Math.max(1, firstUnreadSeq - 25);
          const endSeq = firstUnreadSeq + 25;
          const aroundBoundary = await getMessageRange(roomId, startSeq, endSeq);
          const sortedAround = aroundBoundary.sort((a, b) => a.seq - b.seq);
          if (sortedAround.length > 0) {
            const mergedMap = new Map(windowMessages.map((message) => [message.messageId, message]));
            sortedAround.forEach((message) => {
              mergedMap.set(message.messageId, message);
            });
            windowMessages = Array.from(mergedMap.values()).sort((a, b) => a.seq - b.seq);
          }
        }
      }

      const currentLocal = messagesByRoomRef.current[roomId] || [];
      const incomingByClientMessageId = new Set(
        windowMessages.map((m) => m.clientMessageId).filter(Boolean)
      );

      const optimisticCarry = currentLocal
        .filter((m) => m.clientMessageId && m.messageId.startsWith("temp-"))
        .filter((m) => !incomingByClientMessageId.has(m.clientMessageId))
        .map((m) => {
          if (m.deliveryStatus !== "pending") return m;

          const ageMs = Date.now() - new Date(m.createdAt).getTime();
          if (ageMs <= OPTIMISTIC_CONFIRM_TIMEOUT_MS) return m;

          logChatTelemetry("reconcile_unmatched_pending", {
            roomId,
            messageId: m.messageId,
            clientMessageId: m.clientMessageId,
            ageMs,
          });

          return {
            ...m,
            deliveryStatus: "failed" as const,
          };
        });

      logSendFlow("latest_reconcile", {
        roomId,
        localCount: currentLocal.length,
        apiCount: windowMessages.length,
        optimisticCarryCount: optimisticCarry.length,
      });

      const mergedForView = [...windowMessages, ...optimisticCarry].sort((a, b) => {
        if (a.seq === b.seq) {
          return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
        }
        return a.seq - b.seq;
      });

      const trimmed =
        mergedForView.length > MAX_WINDOW
          ? mergedForView.slice(-MAX_WINDOW)
          : mergedForView;

      oldestSeqByRoom.current[roomId] = trimmed[0]?.seq ?? null;
      newestSeqByRoom.current[roomId] = trimmed.at(-1)?.seq ?? null;
      hasMoreByRoom.current[roomId] = page.hasMore ?? ((trimmed[0]?.seq ?? 1) > 1);

      const newestSeq = newestSeqByRoom.current[roomId];
      hasNewerByRoom.current[roomId] =
        newestSeq != null && latestSeq != null ? newestSeq < latestSeq : false;

      updateWindowMeta(roomId);

      const last = trimmed.at(-1);
      logSendFlow("latest_applied", {
        roomId,
        count: trimmed.length,
        lastMessageId: last?.messageId ?? null,
        lastSeq: last?.seq ?? null,
        lastClientMessageId: last?.clientMessageId ?? null,
      });

      setMessagesByRoom((prev) => ({
        ...prev,
        [roomId]: trimmed,
      }));
    } finally {
      activatingByRoom.current[roomId] = false;
    }
  }, [logChatTelemetry, updateWindowMeta]);

  // Sync refs so handler closure has latest functions
  setActiveRoomRef.current = setActiveRoom;
  upsertMessageRef.current = upsertMessage;

  const setMessageListContainerRef = useCallback((ref: HTMLDivElement | null) => {
    messageListContainerRef.current = ref;
  }, []);

  // ================= SOCKET =================
  // Register MESSAGE_SENT handler exactly once with empty deps. Uses refs to access
  // current setActiveRoom and upsertMessage without re-registering on their changes.
  useEffect(() => {
    return onChatEvent((event) => {
      if (event.type !== ChatEventType.MESSAGE_SENT) return;

      try {
        const roomId = event.payload.roomId;
        const incomingSeq = event.payload.seq;
        const latestKnownSeq = latestSeqByRoom.current[roomId] ?? null;

        if (
          activeRoomIdRef.current === roomId &&
          latestKnownSeq != null &&
          Number.isFinite(incomingSeq) &&
          incomingSeq > latestKnownSeq + 1
        ) {
          logSendFlow("ws_gap_detected", {
            roomId,
            latestKnownSeq,
            incomingSeq,
          });

          // Recover missed live events (brief socket disconnect / tab suspend) by
          // reloading latest for the active room before applying the incoming event.
          void setActiveRoomRef.current(roomId).catch(() => {});
        }

        logSendFlow("ws_message_sent_event", {
          roomId,
          messageId: event.payload.messageId,
          seq: incomingSeq,
          clientMessageId: event.payload.clientMessageId ?? null,
        });

        upsertMessageRef.current(event.payload);

        // =========== AUTO-SCROLL WHEN AT BOTTOM (Task 2.1) ===========
        // Skip scroll for own messages confirmed via WS — the optimistic insert
        // already triggered a scroll when the message was added. Scrolling again
        // here causes a visible double-jump. Only scroll for messages from others.
        const isOwnMessageConfirmation = Boolean(event.payload.clientMessageId);
        if (
          !isOwnMessageConfirmation &&
          isFeatureEnabled("enableAutoScrollOnNewMessage") &&
          activeRoomIdRef.current === roomId
        ) {
          const container = messageListContainerRef.current;
          if (container && isAtBottom(container)) {
            logSendFlow("ws_auto_scroll_triggered", {
              roomId,
              messageId: event.payload.messageId,
            });
            batchScrollToBottom(container);
          }
        }
      } catch (err) {
        console.error("[send-flow] MESSAGE_SENT handler error", err);
      }
    });
  }, []);

  // Reload active-room latest snapshot when socket reconnects to recover
  // messages that may have arrived while disconnected.
  useEffect(() => {
    return onSocketOpen(() => {
      const roomId = activeRoomIdRef.current;
      if (!roomId) return;

      logSendFlow("socket_open_reconcile_active_room", { roomId });
      void setActiveRoomRef.current(roomId).catch(() => {});
    });
  }, []);

  // ================= LOAD OLDER =================
  const loadOlderMessages = useCallback(async (roomId: string) => {
    if (loadingByRoom.current[roomId]) return;
    if (hasMoreByRoom.current[roomId] === false) return;

    const beforeSeq = oldestSeqByRoom.current[roomId];
    if (beforeSeq == null) return;

    loadingByRoom.current[roomId] = true;

    try {
      const page = await getMessagesBefore(roomId, beforeSeq);
      const sorted = page.messages.sort((a, b) => a.seq - b.seq);

      if (!sorted.length) {
        hasMoreByRoom.current[roomId] = false;
        return;
      }

      oldestSeqByRoom.current[roomId] = sorted[0].seq;
      hasMoreByRoom.current[roomId] =
        page.hasMore ?? ((sorted[0]?.seq ?? 1) > 1);

      setMessagesByRoom((prev) => {
        const current = prev[roomId] || [];

        const map = new Map(current.map((m) => [m.messageId, m]));
        sorted.forEach((m) => map.set(m.messageId, m));

        const next = Array.from(map.values()).sort((a, b) => a.seq - b.seq);
        const trimmed = next.length > MAX_WINDOW ? next.slice(-MAX_WINDOW) : next;

        oldestSeqByRoom.current[roomId] = trimmed[0]?.seq ?? null;
        newestSeqByRoom.current[roomId] = trimmed.at(-1)?.seq ?? null;

        const latestSeq = latestSeqByRoom.current[roomId] ?? newestSeqByRoom.current[roomId] ?? null;
        latestSeqByRoom.current[roomId] = latestSeq;
        const newestSeq = newestSeqByRoom.current[roomId];
        hasNewerByRoom.current[roomId] =
          newestSeq != null && latestSeq != null ? newestSeq < latestSeq : false;

        updateWindowMeta(roomId);

        return {
          ...prev,
          [roomId]: trimmed,
        };
      });
    } finally {
      loadingByRoom.current[roomId] = false;
    }
  }, [updateWindowMeta]);

  // ================= LOAD NEWER =================
  const loadNewerMessages = useCallback(async (roomId: string) => {
    if (loadingNewerByRoom.current[roomId]) return;
    if (hasNewerByRoom.current[roomId] === false) return;

    const newestSeq = newestSeqByRoom.current[roomId];
    const latestSeq = latestSeqByRoom.current[roomId];

    if (newestSeq == null || latestSeq == null || newestSeq >= latestSeq) {
      hasNewerByRoom.current[roomId] = false;
      updateWindowMeta(roomId);
      return;
    }

    loadingNewerByRoom.current[roomId] = true;
    try {
      const endSeq = Math.min(newestSeq + 50, latestSeq);
      const range = await getMessageRange(roomId, newestSeq + 1, endSeq);
      const sorted = range.sort((a, b) => a.seq - b.seq);

      if (!sorted.length) {
        hasNewerByRoom.current[roomId] = false;
        updateWindowMeta(roomId);
        return;
      }

      setMessagesByRoom((prev) => {
        const current = prev[roomId] || [];
        const map = new Map(current.map((m) => [m.messageId, m]));
        sorted.forEach((m) => map.set(m.messageId, m));

        const next = Array.from(map.values()).sort((a, b) => a.seq - b.seq);
        const trimmed = next.length > MAX_WINDOW ? next.slice(-MAX_WINDOW) : next;

        oldestSeqByRoom.current[roomId] = trimmed[0]?.seq ?? null;
        newestSeqByRoom.current[roomId] = trimmed.at(-1)?.seq ?? null;

        const currentLatest = latestSeqByRoom.current[roomId] ?? null;
        const observedLatest = Math.max(currentLatest ?? 0, trimmed.at(-1)?.seq ?? 0) || null;
        latestSeqByRoom.current[roomId] = observedLatest;

        const newest = newestSeqByRoom.current[roomId];
        const latest = latestSeqByRoom.current[roomId];
        hasNewerByRoom.current[roomId] =
          newest != null && latest != null ? newest < latest : false;
        hasMoreByRoom.current[roomId] = (trimmed[0]?.seq ?? 1) > 1;
        updateWindowMeta(roomId);

        return {
          ...prev,
          [roomId]: trimmed,
        };
      });
    } finally {
      loadingNewerByRoom.current[roomId] = false;
    }
  }, [updateWindowMeta]);

  // ================= LOAD AROUND =================
  const loadMessagesAround = useCallback(async (roomId: string, messageId: string) => {
    const messages = await getMessagesAround(roomId, messageId);
    const sorted = messages.sort((a, b) => a.seq - b.seq);
    const trimmed = sorted.length > MAX_WINDOW ? sorted.slice(-MAX_WINDOW) : sorted;

    if (trimmed.length) {
      oldestSeqByRoom.current[roomId] = trimmed[0].seq;
      newestSeqByRoom.current[roomId] = trimmed.at(-1)?.seq ?? null;
      hasMoreByRoom.current[roomId] = (trimmed[0]?.seq ?? 1) > 1;

      const latest = latestSeqByRoom.current[roomId] ?? newestSeqByRoom.current[roomId] ?? null;
      latestSeqByRoom.current[roomId] = latest;
      const newest = newestSeqByRoom.current[roomId];
      hasNewerByRoom.current[roomId] =
        newest != null && latest != null ? newest < latest : false;
      updateWindowMeta(roomId);
    }

    setMessagesByRoom((prev) => ({
      ...prev,
      [roomId]: trimmed,
    }));
  }, [updateWindowMeta]);

  // ================= REMOVE =================
  const removeMessage = useCallback((messageId: string, roomId: string) => {
    setMessagesByRoom((prev) => ({
      ...prev,
      [roomId]: (prev[roomId] || []).filter((m) => m.messageId !== messageId),
    }));
  }, []);

  // ================= SEND =================
  const sendMessage = useCallback(
    async (
      roomId: string,
      content: string,
      attachments: Attachment[] = [],
      replyToMessageId?: string | null,
      blocks: MessageBlock[] = [],
      mentionedUserIds: string[] = []
    ) => {
      if (!currentUserId) return;

      const tempId = "temp-" + crypto.randomUUID();
      const clientMessageId = crypto.randomUUID();

      const hasBlockText = blocks.some((block) => block.type === "TEXT" && block.text?.trim());
      const hasBlockAssets = blocks.some((block) => block.type === "ASSET" && block.attachment);

      const temp: ChatMessage = {
        messageId: tempId,
        roomId,
        senderId: currentUserId,
        seq: Number.MAX_SAFE_INTEGER,
        type: hasBlockText && hasBlockAssets ? "MIXED" : hasBlockAssets ? "ATTACHMENT" : "TEXT",
        content,
        replyToMessageId: replyToMessageId ?? null,
        clientMessageId,
        createdAt: new Date().toISOString(),
        editedAt: null,
        deleted: false,
        attachments,
        blocks,
        mentionedUserIds,
        reactions: [],
        deliveryStatus: "pending",
      };

      sendPayloadByClientMessageId.current[clientMessageId] = {
        roomId,
        content,
        attachments,
        replyToMessageId,
        blocks,
        mentionedUserIds,
      };

      logSendFlow("send_start", {
        roomId,
        tempId,
        clientMessageId,
        contentLength: content.length,
        attachmentCount: attachments.length,
        blockCount: blocks.length,
      });

      upsertMessage(temp);
      schedulePendingTimeout(roomId, clientMessageId);

      try {
        const real = await sendMessageApi({
          roomId,
          content,
          attachments,
          replyToMessageId: replyToMessageId ?? undefined,
          blocks,
          clientMessageId,
          mentionedUserIds,
        });

        logSendFlow("send_rest_success", {
          roomId,
          tempId,
          clientMessageId,
          serverMessageId: real.messageId,
          serverSeq: real.seq,
        });

        upsertMessage(real);
      } catch {
        clearPendingTimeout(clientMessageId);
        updateDeliveryStatusByClientMessageId(roomId, clientMessageId, "failed");
        logChatTelemetry("send_failed", { roomId, clientMessageId });
        logSendFlow("send_rest_failed", {
          roomId,
          tempId,
          clientMessageId,
        });
      }
    },
    [
      clearPendingTimeout,
      currentUserId,
      logChatTelemetry,
      schedulePendingTimeout,
      updateDeliveryStatusByClientMessageId,
      upsertMessage,
    ]
  );

  const retryMessage = useCallback(async (roomId: string, messageId: string) => {
    const target = (messagesByRoom[roomId] || []).find((m) => m.messageId === messageId);
    if (!target?.clientMessageId) return;

    const clientMessageId = target.clientMessageId;
    const payload = sendPayloadByClientMessageId.current[clientMessageId];
    if (!payload) {
      logChatTelemetry("retry_missing_payload", { roomId, messageId, clientMessageId });
      logSendFlow("retry_missing_payload", { roomId, messageId, clientMessageId });
      return;
    }

    updateDeliveryStatusByClientMessageId(roomId, clientMessageId, "pending");
    schedulePendingTimeout(roomId, clientMessageId);
    logChatTelemetry("retry_started", { roomId, messageId, clientMessageId });
    logSendFlow("retry_start", { roomId, messageId, clientMessageId });

    try {
      const real = await sendMessageApi({
        roomId: payload.roomId,
        content: payload.content,
        attachments: payload.attachments,
        replyToMessageId: payload.replyToMessageId ?? undefined,
        blocks: payload.blocks,
        clientMessageId,
        mentionedUserIds: payload.mentionedUserIds,
      });
      upsertMessage(real);
      logChatTelemetry("retry_succeeded", { roomId, messageId, clientMessageId });
      logSendFlow("retry_success", {
        roomId,
        messageId,
        clientMessageId,
        serverMessageId: real.messageId,
        serverSeq: real.seq,
      });
    } catch {
      clearPendingTimeout(clientMessageId);
      updateDeliveryStatusByClientMessageId(roomId, clientMessageId, "failed");
      logChatTelemetry("retry_failed", { roomId, messageId, clientMessageId });
      logSendFlow("retry_failed", { roomId, messageId, clientMessageId });
    }
  }, [
    clearPendingTimeout,
    logChatTelemetry,
    messagesByRoom,
    schedulePendingTimeout,
    updateDeliveryStatusByClientMessageId,
    upsertMessage,
  ]);

  return (
    <ChatContext.Provider
      value={{
        messagesByRoom,
        windowMetaByRoom,
        activeRoomId,
        currentUserId,
        replyingTo,
        setReplyingTo,
        setActiveRoom,
        loadOlderMessages,
        loadNewerMessages,
        loadMessagesAround,
        upsertMessage,
        removeMessage,
        setMessageListContainerRef,
        sendMessage,
        retryMessage,
      }}
    >
      {children}
    </ChatContext.Provider>
  );
}

export const useChat = () => {
  const ctx = useContext(ChatContext);
  if (!ctx) throw new Error("useChat must be used inside ChatProvider");
  return ctx;
};
