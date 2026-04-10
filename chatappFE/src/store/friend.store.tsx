import { create } from "zustand";
import {
  getRawStatusApi,
  getIncomingApi,
  getOutgoingApi,
  getBlockedByMeApi,
  getBlockedMeApi,
} from "../api/friend.service";
import type { FriendStatus } from "../types/friend";

export interface FriendState {
  map: Record<string, FriendStatus>;
  unreadFriendRequestCount: number;
  resolve: (userId: string) => Promise<void>;
  setStatus: (userId: string, status: FriendStatus) => void;
  setUnreadCount: (count: number) => void;
  incrementUnreadFriendRequestCount: () => void;
  decrementUnreadFriendRequestCount: () => void;
  clear: () => void;
}

export const useFriendStore = create<FriendState>((set, get) => ({
  map: {},
  unreadFriendRequestCount: 0,

  clear: () => set({ map: {}, unreadFriendRequestCount: 0 }),

  setStatus: (userId, status) =>
    set((s) => ({ map: { ...s.map, [userId]: status } })),

  setUnreadCount: (count) => {
    console.log("[friend.store] setUnreadCount:", count);
    set({ unreadFriendRequestCount: count });
  },

  incrementUnreadFriendRequestCount: () => {
    const current = get().unreadFriendRequestCount;
    const next = Math.max(0, current + 1);
    console.log("[friend.store] incrementUnreadFriendRequestCount:", current, "→", next);
    set({ unreadFriendRequestCount: next });
  },

  decrementUnreadFriendRequestCount: () => {
    const current = get().unreadFriendRequestCount;
    const next = Math.max(0, current - 1);
    console.log("[friend.store] decrementUnreadFriendRequestCount:", current, "→", next);
    set({ unreadFriendRequestCount: next });
  },

  resolve: async (userId: string) => {
    const existing = get().map[userId];
    if (existing) return; // tránh gọi lại API nếu đã biết

    try {
      const raw = await getRawStatusApi(userId);

      if (raw === "ACCEPTED") {
        set((s) => ({ map: { ...s.map, [userId]: "FRIENDS" } }));
        return;
      }

      if (raw === "NONE") {
        set((s) => ({ map: { ...s.map, [userId]: "NONE" } }));
        return;
      }

      if (raw === "PENDING") {
        const [incoming, outgoing] = await Promise.all([
          getIncomingApi(),
          getOutgoingApi(),
        ]);

        if (incoming.includes(userId)) {
          set((s) => ({ map: { ...s.map, [userId]: "REQUEST_RECEIVED" } }));
        } else if (outgoing.includes(userId)) {
          set((s) => ({ map: { ...s.map, [userId]: "REQUEST_SENT" } }));
        } else {
          set((s) => ({ map: { ...s.map, [userId]: "NONE" } }));
        }
        return;
      }

      if (raw === "BLOCKED") {
        const [blockedByMe, blockedMe] = await Promise.all([
          getBlockedByMeApi(),
          getBlockedMeApi(),
        ]);

        if (blockedByMe.includes(userId)) {
          set((s) => ({ map: { ...s.map, [userId]: "BLOCKED_BY_ME" } }));
        } else if (blockedMe.includes(userId)) {
          set((s) => ({ map: { ...s.map, [userId]: "BLOCKED_ME" } }));
        }
      }
    } catch (err) {
      console.error("Failed to resolve friend status", err);
    }
  },
}));
