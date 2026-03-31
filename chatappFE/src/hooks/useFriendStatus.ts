import { useEffect } from "react";
import { useAuth } from "../store/auth.store";
import { useFriendStore } from "../store/friend.store";

export const useFriendStatus = (targetUserId?: string) => {
  const { accessToken, userId: myId } = useAuth();

  const status = useFriendStore((s) =>
    targetUserId ? s.map[targetUserId] : undefined
  );

  const resolve = useFriendStore((s) => s.resolve);

  useEffect(() => {
    if (!accessToken || !targetUserId || targetUserId === myId) return;
    if (!status) resolve(targetUserId);
  }, [accessToken, targetUserId, myId, status, resolve]);

  if (targetUserId && targetUserId === myId) return "SELF";

  return status;
};