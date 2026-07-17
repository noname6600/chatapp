import { useEffect } from "react";
import { useAuth } from "./auth.store";
import { useFriendStore } from "./friend.store";
import {
  onFriendshipEvent,
  processFriendshipEvent,
} from "../websocket/friendship.socket";
import { getUnreadFriendRequestCountApi } from "../api/friend.service";

/**
 * Hook to initialize friendship socket and fetch unread count
 */
export function useFriendshipInitialization() {
  const { accessToken } = useAuth();
  const setUnreadCount = useFriendStore((state) => state.setUnreadCount);
  const clearFriendState = useFriendStore((state) => state.clear);

  // Fetch unread count via HTTP on app load
  useEffect(() => {
    if (!accessToken) {
      setUnreadCount(0)
      return;
    }

    const fetchUnreadCount = async () => {
      try {
        console.log("[friendship] Fetching unread count via HTTP...");
        const response = await getUnreadFriendRequestCountApi();
        setUnreadCount(response.unreadCount);
        console.log("[friendship] ✅ Loaded unread count:", response.unreadCount);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err)
        if (message.toLowerCase().includes("session expired")) {
          setUnreadCount(0)
          return
        }
        console.error("[friendship] ❌ Failed to fetch unread count:", err);
      }
    };

    fetchUnreadCount();
  }, [accessToken, setUnreadCount]);

  // Register event handler for incoming friendship events
  useEffect(() => {
    if (!accessToken) {
      clearFriendState()
      return;
    }

    const unsubscribeEvent = onFriendshipEvent((event) => {
      processFriendshipEvent(event);
    });

    return () => {
      unsubscribeEvent();
    };
  }, [accessToken, clearFriendState]);
}
