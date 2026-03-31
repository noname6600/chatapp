import { create } from "zustand";
import { persist } from "zustand/middleware";
import { getUsersBulkApi } from "../api/user.service";
import type { UserProfile } from "../types/user";

interface UserState {
  users: Record<string, UserProfile>;
  lastFetchedMap: Record<string, number>;

  fetchUsers: (ids: string[]) => Promise<void>;
  updateUserLocal: (profile: UserProfile) => void;
}

const ONE_DAY = 24 * 60 * 60 * 1000;
const MENTION_DEBUG = import.meta.env.DEV;

export const useUserStore = create<UserState>()(
  persist(
    (set, get) => ({
      users: {},
      lastFetchedMap: {},

      updateUserLocal: (profile: UserProfile) =>
        set((state) => ({
          users: {
            ...state.users,
            [profile.accountId]: profile,
          },
          lastFetchedMap: {
            ...state.lastFetchedMap,
            [profile.accountId]: Date.now(),
          },
        })),

      fetchUsers: async (ids: string[]) => {
        if (!ids.length) {
          if (MENTION_DEBUG) {
            console.log("[mention-debug] fetchUsers skipped: empty id list");
          }
          return;
        }

        const uniqueIds = Array.from(new Set(ids.filter(Boolean)));
        const now = Date.now();

        const { users, lastFetchedMap } = get();

        const needFetch = uniqueIds.filter((id) => {
          const last = lastFetchedMap[id];

          if (!users[id]) return true;
          if (!last) return true;

          return now - last > ONE_DAY;
        });

        if (!needFetch.length) {
          if (MENTION_DEBUG) {
            console.log("[mention-debug] fetchUsers skipped: all users served from cache", {
              requestedIds: ids,
            });
          }
          return;
        }

        try {
          if (MENTION_DEBUG) {
            console.log("[mention-debug] fetchUsers request", {
              requestedIds: ids,
              needFetch,
            });
          }

          const res = await getUsersBulkApi(needFetch);

          if (MENTION_DEBUG) {
            res.forEach((u) => {
              console.log(
                `[mention-debug] bulk-response id=${u.accountId} username=${u.username || "<empty>"} displayName=${u.displayName || "<empty>"}`
              );
            });
          }

          set((state) => {
            const updatedUsers = { ...state.users };
            const updatedTimes = { ...state.lastFetchedMap };

            res.forEach((u) => {
              const previous = updatedUsers[u.accountId];
              updatedUsers[u.accountId] = {
                ...previous,
                ...u,
                username: u.username || previous?.username || "",
                displayName:
                  u.displayName ||
                  previous?.displayName ||
                  u.username ||
                  previous?.username ||
                  u.accountId,
              };
              updatedTimes[u.accountId] = now;
            });

            return {
              users: updatedUsers,
              lastFetchedMap: updatedTimes,
            };
          });
        } catch (e) {
          console.error("fetchUsers failed", e);
        }
      },
    }),
    {
      name: "user-cache",
      partialize: (state) => ({
        users: state.users,
        lastFetchedMap: state.lastFetchedMap,
      }),
    }
  )
);