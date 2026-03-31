import { useState, useCallback, useRef } from "react";
import { toggleReactionApi } from "../api/chat.service";
import { useChat } from "../store/chat.store";
import type { ChatMessage } from "../types/message";
import { toggleReactionLocally } from "../utils/reactionState";
import { trackEvent } from "../utils/analytics";

interface UseReactionParams {
  messageId: string;
  roomId: string;
}

export function useReaction({ messageId, roomId }: UseReactionParams) {
  const [loading, setLoading] = useState(false);
  const { messagesByRoom, upsertMessage } = useChat();
  const debounceTimer = useRef<NodeJS.Timeout | null>(null);
  const previousStateRef = useRef<ChatMessage | null>(null);

  const message = messagesByRoom[roomId]?.find(
    (m: ChatMessage) => m.messageId === messageId
  );

  /**
   * Check if user has reacted with the given emoji
   */
  const hasUserReacted = useCallback(
    (emoji: string) => {
      return message?.reactions?.some(
        (r) => r.emoji === emoji && r.reactedByMe === true
      ) ?? false;
    },
    [message]
  );

  /**
   * Toggle reaction with debounce and idempotent logic
   * - Checks state before API call
   * - Applies optimistic update
   * - Calls API
   * - Reverts on error
   * - Re-validates state after response
   */
  const toggleReaction = useCallback(
    async (emoji: string) => {
      // Clear any pending debounce timer
      if (debounceTimer.current) {
        clearTimeout(debounceTimer.current);
      }

      // Debounce to prevent rapid clicks
      debounceTimer.current = setTimeout(async () => {
        if (loading || !message) return;

        try {
          setLoading(true);

          const currentReaction = (message.reactions || []).find(
            (r) => r.emoji === emoji
          );
          const action = currentReaction?.reactedByMe ? "remove" : "add";
          // If reaction exists but reactedByMe is unknown (undefined/false from aggregate API),
          // optimistic toggle can be wrong. In that case wait for authoritative WS event.
          const canApplyOptimistic =
            !currentReaction || currentReaction.reactedByMe === true;

          // Save previous state for rollback
          previousStateRef.current = {
            ...message,
            reactions: [...(message.reactions || [])],
          };

          if (canApplyOptimistic) {
            const newReactions = toggleReactionLocally(
              message.reactions || [],
              emoji
            );
            upsertMessage({ ...message, reactions: newReactions });
          }

          // Call API to persist change
          await toggleReactionApi(messageId, emoji);

          trackEvent("reaction_toggle", {
            messageId,
            roomId,
            emoji,
            action,
            optimisticApplied: canApplyOptimistic,
          });

          // No stale-state comparison here. Authoritative reconciliation happens via WS event.
        } catch (error) {
          // Revert on failure
          if (previousStateRef.current) {
            upsertMessage(previousStateRef.current);
          }
          console.error("Failed to toggle reaction:", error);
          // TODO: Show error toast to user
        } finally {
          setLoading(false);
        }
      }, 150); // 150ms debounce to catch rapid clicks
    },
    [loading, message, messageId, upsertMessage]
  );

  return {
    toggleReaction,
    loading,
    hasUserReacted: useCallback(
      (emoji: string) => hasUserReacted(emoji),
      [hasUserReacted]
    ),
  };
}
