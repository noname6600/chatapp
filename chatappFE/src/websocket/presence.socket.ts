import { PresenceEventType } from "../constants/presenceEvents";
import { getGlobalPresenceApi } from "../api/presence.service";
import {
  PRESENCE_AWAY_THRESHOLD_MS,
  PRESENCE_HEARTBEAT_INTERVAL_MS,
  PRESENCE_OFFLINE_GRACE_MS,
} from "../config/presence.config";
import { usePresenceStore } from "../store/presence.store";
import type { PresenceStatus, PresenceUserState } from "../types/presence";
import { getWsEndpoint } from "../config/ws.config";

export interface PresenceWsEvent<T = unknown> {
  type: PresenceEventType;
  payload: T;
}

let socket: WebSocket | null = null;
let reconnectTimeout: number | null = null;
let heartbeatInterval: number | null = null;
let manualClose = false;
let reconnectFailureCount = 0;
let reconnectSnapshotInFlight: Promise<void> | null = null;
let lastReconnectSnapshotAt = 0;

const joinedRooms = new Set<string>();
const pendingCommands: unknown[] = [];

const openHandlers = new Set<() => void>();
const eventHandlers = new Set<(event: PresenceWsEvent) => void>();
const offlineTimers = new Map<string, number>();

const WS_URL = getWsEndpoint("PRESENCE");
const BACKOFF_BASE = 1000;
const BACKOFF_MAX = 30000;
const RECONNECT_SNAPSHOT_COOLDOWN_MS = 2000;

const calculateBackoffDelay = (failureCount: number): number => {
  const exponentialDelay = BACKOFF_BASE * Math.pow(2, Math.max(0, failureCount - 1));
  return Math.min(exponentialDelay, BACKOFF_MAX);
};

const syncGlobalPresenceAfterReconnect = () => {
  const now = Date.now();

  if (reconnectSnapshotInFlight) {
    return reconnectSnapshotInFlight;
  }

  if (now - lastReconnectSnapshotAt < RECONNECT_SNAPSHOT_COOLDOWN_MS) {
    return Promise.resolve();
  }

  reconnectSnapshotInFlight = getGlobalPresenceApi()
    .then((users) => {
      usePresenceStore.getState().setGlobalPresence(users);
    })
    .catch(() => {})
    .finally(() => {
      lastReconnectSnapshotAt = Date.now();
      reconnectSnapshotInFlight = null;
    });

  return reconnectSnapshotInFlight;
};

let activityTrackingBound = false;
let lastActivityAt = Date.now();

const refreshActivity = () => {
  lastActivityAt = Date.now();
};

const isClientActive = () => {
  return Date.now() - lastActivityAt < PRESENCE_AWAY_THRESHOLD_MS;
};

const cancelPendingOffline = (userId: string) => {
  const timer = offlineTimers.get(userId);
  if (timer) {
    clearTimeout(timer);
    offlineTimers.delete(userId);
  }
};

const clearPendingOfflines = () => {
  offlineTimers.forEach((timer) => clearTimeout(timer));
  offlineTimers.clear();
};

const scheduleOffline = (userId: string) => {
  cancelPendingOffline(userId);

  const timer = window.setTimeout(() => {
    offlineTimers.delete(userId);
    const state = usePresenceStore.getState();
    state.setUserStatus(userId, "OFFLINE");
    state.clearUserTypingEverywhere(userId);
  }, PRESENCE_OFFLINE_GRACE_MS);

  offlineTimers.set(userId, timer);
};

const sendHeartbeat = () => {
  sendPresenceCommand({
    type: PresenceEventType.USER_HEARTBEAT,
    active: isClientActive(),
  });
};

const handleActivityChange = () => {
  refreshActivity();
};

const handleVisibilityChange = () => {
  if (document.visibilityState === "visible") {
    refreshActivity();
  }

  sendHeartbeat();
};

const bindActivityTracking = () => {
  if (activityTrackingBound || typeof window === "undefined") return;

  activityTrackingBound = true;
  refreshActivity();

  window.addEventListener("focus", handleVisibilityChange);
  window.addEventListener("blur", handleVisibilityChange);
  window.addEventListener("keydown", handleActivityChange);
  window.addEventListener("pointerdown", handleActivityChange);
  window.addEventListener("touchstart", handleActivityChange);
  document.addEventListener("visibilitychange", handleVisibilityChange);
};

const unbindActivityTracking = () => {
  if (!activityTrackingBound || typeof window === "undefined") return;

  activityTrackingBound = false;

  window.removeEventListener("focus", handleVisibilityChange);
  window.removeEventListener("blur", handleVisibilityChange);
  window.removeEventListener("keydown", handleActivityChange);
  window.removeEventListener("pointerdown", handleActivityChange);
  window.removeEventListener("touchstart", handleActivityChange);
  document.removeEventListener("visibilitychange", handleVisibilityChange);
};

const getSnapshotUsers = (payload: unknown): PresenceUserState[] => {
  if (payload && typeof payload === "object" && Array.isArray((payload as { users?: unknown[] }).users)) {
    return (payload as { users: PresenceUserState[] }).users;
  }

  return [];
};

export const connectPresenceSocket = () => {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;

  const token = localStorage.getItem("access_token");
  if (!token) return;

  manualClose = false;
  refreshActivity();
  socket = new WebSocket(`${WS_URL}?token=${token}`);

  socket.onopen = () => {
    reconnectFailureCount = 0;
    if (reconnectTimeout != null) {
      clearTimeout(reconnectTimeout);
      reconnectTimeout = null;
    }

    startHeartbeat();
    bindActivityTracking();

    void syncGlobalPresenceAfterReconnect();

    joinedRooms.forEach((roomId) => {
      socket?.send(
        JSON.stringify({
          type: PresenceEventType.ROOM_JOIN,
          roomId,
        })
      );
    });

    while (pendingCommands.length) {
      socket?.send(JSON.stringify(pendingCommands.shift()));
    }

    sendHeartbeat();

    openHandlers.forEach((h) => h());
  };

  socket.onmessage = (event) => {
    try {
      const data: PresenceWsEvent = JSON.parse(event.data);
      handlePresenceEvent(data);
      eventHandlers.forEach((h) => h(data));
    } catch {}
  };

  socket.onclose = () => {
    stopHeartbeat();
    unbindActivityTracking();
    socket = null;

    if (!manualClose && localStorage.getItem("access_token")) {
      reconnectFailureCount += 1;
      reconnectTimeout = window.setTimeout(
        connectPresenceSocket,
        calculateBackoffDelay(reconnectFailureCount)
      );
    }
  };

  socket.onerror = () => socket?.close();
};

export const disconnectPresenceSocket = () => {
  manualClose = true;

  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout);
    reconnectTimeout = null;
  }

  stopHeartbeat();
  unbindActivityTracking();
  clearPendingOfflines();
  joinedRooms.clear();
  pendingCommands.length = 0;
  reconnectFailureCount = 0;
  reconnectSnapshotInFlight = null;

  socket?.close();
  socket = null;
};

const startHeartbeat = () => {
  stopHeartbeat();
  heartbeatInterval = window.setInterval(() => {
    sendHeartbeat();
  }, PRESENCE_HEARTBEAT_INTERVAL_MS);
};

const stopHeartbeat = () => {
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval);
    heartbeatInterval = null;
  }
};

type PresenceCommand =
  | { type: PresenceEventType.USER_HEARTBEAT; active?: boolean }
  | { type: PresenceEventType.USER_TYPING; roomId: string }
  | { type: PresenceEventType.USER_STOP_TYPING; roomId: string }
  | { type: PresenceEventType.ROOM_JOIN; roomId: string }
  | { type: PresenceEventType.ROOM_LEAVE; roomId: string };

const sendPresenceCommand = (data: PresenceCommand) => {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    pendingCommands.push(data);
    return;
  }
  socket.send(JSON.stringify(data));
};

export const sendTyping = (roomId: string) => {
  sendPresenceCommand({ type: PresenceEventType.USER_TYPING, roomId });
};

export const sendStopTyping = (roomId: string) => {
  sendPresenceCommand({ type: PresenceEventType.USER_STOP_TYPING, roomId });
};

export const joinPresenceRoom = (roomId: string) => {
  if (!roomId) return;

  joinedRooms.add(roomId);

  sendPresenceCommand({
    type: PresenceEventType.ROOM_JOIN,
    roomId,
  });
};

export const leavePresenceRoom = (roomId: string) => {
  if (!roomId) return;

  const wasJoined = joinedRooms.has(roomId);
  joinedRooms.delete(roomId);

  if (wasJoined) {
    sendPresenceCommand({
      type: PresenceEventType.ROOM_LEAVE,
      roomId,
    });
  }

  const state = usePresenceStore.getState();
  state.clearRoomOnline(roomId);
  state.clearRoomTyping(roomId);
};

export const onPresenceEvent = (handler: (event: PresenceWsEvent) => void) => {
  eventHandlers.add(handler);
  return () => {
    eventHandlers.delete(handler);
  };
};

export const onPresenceOpen = (handler: () => void) => {
  openHandlers.add(handler);
  return () => {
    openHandlers.delete(handler);
  };
};

function handlePresenceEvent(event: PresenceWsEvent) {
  const state = usePresenceStore.getState();

  switch (event.type) {
    case PresenceEventType.USER_ONLINE: {
      const { userId, status } = event.payload as {
        userId: string;
        status?: PresenceStatus;
      };
      if (userId) {
        cancelPendingOffline(userId);
        state.setUserStatus(userId, status ?? "ONLINE");
      }
      break;
    }

    case PresenceEventType.USER_OFFLINE: {
      const { userId } = event.payload as { userId: string };
      if (userId) {
        scheduleOffline(userId);
      }
      break;
    }

    case PresenceEventType.USER_STATUS_CHANGED: {
      const { userId, status } = event.payload as {
        userId: string;
        status: PresenceStatus;
      };

      if (userId && status) {
        cancelPendingOffline(userId);
        state.setUserStatus(userId, status);
      }
      break;
    }

    case PresenceEventType.USER_TYPING: {
      const { roomId, userId } = event.payload as {
        roomId: string;
        userId: string;
      };
      if (roomId && userId) state.setUserTyping(roomId, userId);
      break;
    }

    case PresenceEventType.USER_STOP_TYPING: {
      const { roomId, userId } = event.payload as {
        roomId: string;
        userId: string;
      };
      if (roomId && userId) state.setUserStopTyping(roomId, userId);
      break;
    }

    case PresenceEventType.ROOM_ONLINE_USERS: {
      const { roomId } = event.payload as {
        roomId: string;
      };
      const users = getSnapshotUsers(event.payload);

      if (roomId) {
        state.setRoomPresence(roomId, users);
      }
      break;
    }

    case PresenceEventType.GLOBAL_SNAPSHOT: {
      const users = getSnapshotUsers(event.payload);
      users.forEach((user) => {
        if (user.status !== "OFFLINE") {
          cancelPendingOffline(user.userId);
        }
      });
      state.setGlobalPresence(users);
      break;
    }

    case PresenceEventType.ROOM_SNAPSHOT: {
      const { roomId } = event.payload as {
        roomId: string;
      };
      const users = getSnapshotUsers(event.payload);
      if (roomId) {
        state.setRoomPresence(roomId, users);
      }
      break;
    }
  }
}
