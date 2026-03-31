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
  resolve: (userId: string) => Promise<void>;
  setStatus: (userId: string, status: FriendStatus) => void;
  clear: () => void;
}

export const useFriendStore = create<FriendState>((set, get) => ({
  map: {},

  clear: () => set({ map: {} }),

  setStatus: (userId, status) =>
    set((s) => ({ map: { ...s.map, [userId]: status } })),

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
