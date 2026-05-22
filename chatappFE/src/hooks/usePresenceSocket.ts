import { useEffect } from "react";
import { connectRealtimeSocket } from "../websocket/realtime.socket";
import { usePresenceStore } from "../store/presence.store";

export const usePresenceSocket = () => {
  const clearAllOnline = usePresenceStore((s) => s.clearAllOnline);

  useEffect(() => {
    const token = localStorage.getItem("access_token");
    if (!token) return;

    connectRealtimeSocket();

    return () => {
      clearAllOnline();
    };
  }, [clearAllOnline]);
};
