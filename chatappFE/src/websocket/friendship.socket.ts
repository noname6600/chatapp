import { useFriendStore } from "../store/friend.store";
import { getWsEndpoint } from "../config/ws.config";

export enum FriendshipEventType {
  FRIEND_REQUEST_RECEIVED = "FRIEND_REQUEST_RECEIVED",
  FRIEND_REQUEST_ACCEPTED = "FRIEND_REQUEST_ACCEPTED",
  FRIEND_REQUEST_DECLINED = "FRIEND_REQUEST_DECLINED",
  FRIEND_REQUEST_CANCELLED = "FRIEND_REQUEST_CANCELLED",
  FRIEND_STATUS_CHANGED = "FRIEND_STATUS_CHANGED",
}

export interface FriendshipWsEvent {
  type: FriendshipEventType;
  data: Record<string, any>;
}

const getCurrentUserId = () => localStorage.getItem("my_user_id");

const getCounterpartyId = (event: FriendshipWsEvent) => {
  const currentUserId = getCurrentUserId();
  if (!currentUserId) return null;

  if (event.data.senderId && event.data.recipientId) {
    return event.data.senderId === currentUserId
      ? event.data.recipientId
      : event.data.senderId;
  }

  if (event.data.userLow && event.data.userHigh) {
    return event.data.userLow === currentUserId
      ? event.data.userHigh
      : event.data.userHigh === currentUserId
        ? event.data.userLow
        : null;
  }

  return null;
};

// WebSocket connection
let socket: WebSocket | null = null;
let reconnectTimeout: number | null = null;
let manualClose = false;

const eventHandlers = new Set<(event: FriendshipWsEvent) => void>();
const openHandlers = new Set<() => void>();

const WS_URL = getWsEndpoint("FRIEND");
const RECONNECT_DELAY = 3000;

/**
 * Connect to friendship WebSocket endpoint
 */
export const connectFriendshipSocket = () => {
  if (
    socket &&
    (socket.readyState === WebSocket.OPEN ||
      socket.readyState === WebSocket.CONNECTING)
  ) {
    console.log("[friendship-socket] Already connected or connecting");
    return;
  }

  const token = localStorage.getItem("access_token");
  if (!token) {
    console.error("[friendship-socket] No access token found");
    return;
  }

  if (reconnectTimeout != null) {
    clearTimeout(reconnectTimeout);
    reconnectTimeout = null;
  }

  manualClose = false;
  const wsUrl = `${WS_URL}?token=${token}`;
  console.log("[friendship-socket] Attempting connection to:", WS_URL, "with token:", token.substring(0, 30) + "...");
  socket = new WebSocket(wsUrl);

  socket.onopen = () => {
    console.log("[friendship-socket] Connected");
    openHandlers.forEach((handler) => handler());
  };

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      console.log("[friendship-socket] Received:", data);
      handleFriendshipEvent(data);
    } catch (err) {
      console.error("[friendship-socket] Parse error:", err);
    }
  };

  socket.onclose = () => {
    if (manualClose) {
      console.log("[friendship-socket] Disconnected after manual cleanup")
    } else {
      console.warn("[friendship-socket] Disconnected unexpectedly", {
        readyState: socket?.readyState,
      })
    }
    socket = null;

    if (!manualClose && localStorage.getItem("access_token")) {
      console.log("[friendship-socket] Reconnecting in 3s...");
      reconnectTimeout = window.setTimeout(
        connectFriendshipSocket,
        RECONNECT_DELAY
      );
    } else {
      console.log("[friendship-socket] Reconnect suppressed (manual close or no token)");
    }
  };

  socket.onerror = (err) => {
    console.error("[friendship-socket] Error:", err);
    console.error("[friendship-socket] WebSocket state:", socket?.readyState, "(0=CONNECTING, 1=OPEN, 2=CLOSING, 3=CLOSED)");
    console.error("[friendship-socket] Endpoint:", WS_URL);
    socket?.close();
  };
};

/**
 * Disconnect from friendship WebSocket
 */
export const disconnectFriendshipSocket = () => {
  manualClose = true;
  console.log("[friendship-socket] Manual disconnect requested (expected during cleanup)");

  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout);
    reconnectTimeout = null;
  }

  socket?.close();
  socket = null;
};

/**
 * Register a handler for friendship WebSocket events
 * Returns unsubscribe function
 */
export const onFriendshipEvent = (
  handler: (event: FriendshipWsEvent) => void
) => {
  eventHandlers.add(handler);
  return () => eventHandlers.delete(handler);
};

/**
 * Register a handler for when friendship socket opens
 * Returns unsubscribe function
 */
export const onFriendshipSocketOpen = (handler: () => void) => {
  openHandlers.add(handler);
  return () => openHandlers.delete(handler);
};

/**
 * Handle incoming friendship WebSocket event
 */
export function handleFriendshipEvent(msg: any) {
  if (!msg || !msg.type) return;

  const event: FriendshipWsEvent = {
    type: msg.type as FriendshipEventType,
    data: msg.data || {},
  };

  // Dispatch to all registered handlers
  eventHandlers.forEach((handler) => {
    try {
      handler(event);
    } catch (err) {
      console.error("[friendship-socket] Handler error:", err);
    }
  });
}

/**
 * Process friendship events and update store state
 */
export function processFriendshipEvent(event: FriendshipWsEvent) {
  const state = useFriendStore.getState();
  const counterpartyId = getCounterpartyId(event);

  switch (event.type) {
    case FriendshipEventType.FRIEND_REQUEST_RECEIVED: {
      // Increment unread count when new request received
      state.incrementUnreadFriendRequestCount();
      if (counterpartyId) {
        state.setStatus(counterpartyId, "REQUEST_RECEIVED");
      }
      console.log(
        "[friendship] Friend request received from:",
        event.data.senderId
      );
      break;
    }

    case FriendshipEventType.FRIEND_REQUEST_ACCEPTED: {
      // Decrement unread count when request accepted
      state.decrementUnreadFriendRequestCount();
      if (counterpartyId) {
        state.setStatus(counterpartyId, "FRIENDS");
      }
      console.log(
        "[friendship] Friend request accepted from:",
        event.data.senderId
      );
      break;
    }

    case FriendshipEventType.FRIEND_REQUEST_DECLINED:
    case FriendshipEventType.FRIEND_REQUEST_CANCELLED: {
      // Decrement unread count when request declined/cancelled
      state.decrementUnreadFriendRequestCount();
      if (counterpartyId) {
        state.setStatus(counterpartyId, "NONE");
      }
      console.log("[friendship] Friend request declined/cancelled");
      break;
    }

    case FriendshipEventType.FRIEND_STATUS_CHANGED: {
      // Handle friendship status changes (unfriend, block, unblock)
      if (counterpartyId) {
        const eventType = event.data.eventType as string;
        if (eventType === "FRIEND_UNFRIENDED") {
          state.setStatus(counterpartyId, "NONE");
        } else if (eventType === "FRIEND_BLOCKED") {
          const myId = getCurrentUserId();
          if (event.data.actionUserId === myId) {
            state.setStatus(counterpartyId, "BLOCKED_BY_ME");
          } else {
            state.setStatus(counterpartyId, "BLOCKED_ME");
          }
        } else if (eventType === "FRIEND_UNBLOCKED") {
          state.setStatus(counterpartyId, "NONE");
        }
      }
      console.log(
        "[friendship] Status changed:",
        event.data.newStatus,
        "users:",
        event.data.userLow,
        event.data.userHigh
      );
      break;
    }

    default:
      console.warn("[friendship] Unknown event type:", event.type);
  }
}
