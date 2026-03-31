import { useCallback, useMemo, useState } from "react";
import { useUserStore } from "../store/user.store";
import type { MentionSuggestion } from "../components/chat/MentionAutocomplete";
import { filterMentionSuggestions } from "../components/chat/mention.helpers";

interface UseMentionOptions {
  triggerCharacter?: string;
  maxSuggestions?: number;
  candidateUserIds?: string[];
  currentUserId?: string | null;
}

export function useMention(options: UseMentionOptions = {}) {
  const {
    triggerCharacter = "@",
    maxSuggestions = 5,
    candidateUserIds = [],
    currentUserId = null,
  } = options;
  const users = useUserStore((s) => s.users);

  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [cursorPosition, setCursorPosition] = useState<{
    x: number;
    y: number;
  } | null>(null);

  // Detect mention trigger in HTML content
  const detectMention = useCallback(
    (html: string, cursorOffset: number) => {
      // Simple detection: find last @ before cursor
      const beforeCursor = html.substring(0, cursorOffset);
      const atIndex = beforeCursor.lastIndexOf(triggerCharacter);

      if (atIndex === -1) {
        setIsOpen(false);
        return;
      }

      // Check if @ is preceded by space or start of content
      if (atIndex > 0) {
        const beforeAt = html[atIndex - 1];
        if (!/\s/.test(beforeAt)) {
          setIsOpen(false);
          return;
        }
      }

      // Extract query text after @
      const queryText = beforeCursor.substring(atIndex + 1);

      // Keep suggestions open for bare '@' so users can pick from defaults.
      if (!queryText.trim()) {
        setQuery("");
        setSelectedIndex(0);
        setIsOpen(true);
        return;
      }

      // Check if there's a space after query (if so, mention is complete)
      const afterCursor = html.substring(cursorOffset, cursorOffset + 20);
      if (/\s/.test(queryText) || (queryText && /\s/.test(afterCursor[0]))) {
        setIsOpen(false);
        return;
      }

      setQuery(queryText.toLowerCase());
      setSelectedIndex(0);
      setIsOpen(true);
    },
    [triggerCharacter]
  );

  // Get filtered suggestions
  const suggestions = useMemo(() => {
    if (!isOpen) return [];

    return filterMentionSuggestions(
      users,
      candidateUserIds,
      currentUserId,
      query,
      maxSuggestions
    );
  }, [candidateUserIds, currentUserId, isOpen, maxSuggestions, query, users]);

  const selectMention = useCallback(
    (suggestion: MentionSuggestion) => {
      setIsOpen(false);
      setQuery("");
      setSelectedIndex(0);
      return suggestion;
    },
    []
  );

  const handleKeyDown = useCallback(
    (
      e: React.KeyboardEvent
    ): MentionSuggestion | "move-up" | "move-down" | null => {
      if (!isOpen) return null;

      if (e.key === "ArrowDown") {
        e.preventDefault();
        const nextIndex = (selectedIndex + 1) % suggestions.length;
        setSelectedIndex(nextIndex);
        return "move-down";
      }

      if (e.key === "ArrowUp") {
        e.preventDefault();
        const prevIndex =
          selectedIndex === 0 ? suggestions.length - 1 : selectedIndex - 1;
        setSelectedIndex(prevIndex);
        return "move-up";
      }

      if (e.key === "Enter" && suggestions.length > 0) {
        e.preventDefault();
        const selected = suggestions[selectedIndex];
        return selected;
      }

      if (e.key === "Escape") {
        setIsOpen(false);
        return null;
      }

      return null;
    },
    [isOpen, selectedIndex, suggestions]
  );

  return {
    isOpen,
    query,
    suggestions,
    selectedIndex,
    cursorPosition,
    detectMention,
    selectMention,
    handleKeyDown,
    setCursorPosition,
    setIsOpen,
  };
}
