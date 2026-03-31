import { useEffect } from "react";
import { connectPresenceSocket, disconnectPresenceSocket } from "../websocket/presence.socket";
import { usePresenceStore } from "../store/presence.store";

export const usePresenceSocket = () => {
  const clearAllOnline = usePresenceStore((s) => s.clearAllOnline);

  useEffect(() => {
    const token = localStorage.getItem("access_token");
    if (!token) return;

    connectPresenceSocket();

    return () => {
      disconnectPresenceSocket();
      clearAllOnline();
    };
  }, [clearAllOnline]);
};
