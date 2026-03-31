import { useMemo } from "react";
import { usePresenceStore } from "../../store/presence.store";
import { useChat } from "../../store/chat.store";
import TypingUsers from "./TypingUsers";

export default function TypingIndicator({ roomId }: { roomId: string }) {
  const typingMap = usePresenceStore((s) => s.typingByRoom?.[roomId]);
  const { currentUserId } = useChat();
  const visibleTypingUsers = useMemo(
    () => Object.keys(typingMap ?? {}).filter((id) => id !== currentUserId),
    [typingMap, currentUserId]
  );

  if (!visibleTypingUsers.length) return null;

  return (
    <div className="shrink-0 border-t bg-white px-3 py-2">
      <TypingUsers userIds={visibleTypingUsers} />
    </div>
  );
}
