import { useCallback } from "react";
import { useChat } from "../store/chat.store";
import type { ChatMessage } from "../types/message";

export function useReply() {
  const { replyingTo, setReplyingTo } = useChat();

  const clearReply = useCallback(() => {
    setReplyingTo(null);
  }, [setReplyingTo]);

  const setReply = useCallback(
    (message: ChatMessage) => {
      setReplyingTo(message);
    },
    [setReplyingTo]
  );

  return {
    replyingTo,
    setReply,
    clearReply,
  };
}
