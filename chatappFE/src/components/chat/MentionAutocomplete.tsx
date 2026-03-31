import { useRef, useEffect } from "react";
import clsx from "clsx";
import UserAvatar from "../user/UserAvatar";
import { usePresenceStore } from "../../store/presence.store";

export interface MentionSuggestion {
  userId: string;
  displayName: string;
  username: string;
  avatarUrl?: string;
}

function PresenceDot({ status }: { status: "ONLINE" | "AWAY" | "OFFLINE" }) {
  const dotClass =
    status === "ONLINE"
      ? "bg-green-500"
      : status === "AWAY"
        ? "bg-yellow-500"
        : "bg-gray-400";

  return <span className={clsx("inline-block h-2 w-2 rounded-full", dotClass)} aria-hidden="true" />;
}

const buildRightMentionLabel = (suggestion: MentionSuggestion): string => {
  const username = suggestion.username?.trim();
  if (username) return `@${username}`;

  return "@unknown_username";
};

interface MentionAutocompleteProps {
  suggestions: MentionSuggestion[];
  isOpen: boolean;
  selectedIndex?: number;
  onSelect: (suggestion: MentionSuggestion) => void;
  position?: {
    x: number;
    y: number;
  };
}

export default function MentionAutocomplete({
  suggestions,
  isOpen,
  selectedIndex = 0,
  onSelect,
  position,
}: MentionAutocompleteProps) {
  const listRef = useRef<HTMLDivElement>(null);
  const getUserStatus = usePresenceStore((s) => s.getUserStatus);

  // Scroll selected item into view
  useEffect(() => {
    if (listRef.current && selectedIndex >= 0) {
      const items = listRef.current.querySelectorAll("[role='option']");
      items[selectedIndex]?.scrollIntoView({ block: "nearest" });
    }
  }, [selectedIndex]);

  if (!isOpen || suggestions.length === 0) return null;

  return (
    <div
      ref={listRef}
      style={
        position
          ? {
              position: "absolute",
              left: `${position.x}px`,
              top: `${position.y}px`,
              zIndex: 1000,
            }
          : undefined
      }
      className={clsx(
        "bg-white border border-gray-300 rounded-lg shadow-lg",
        "max-h-48 overflow-y-auto",
        position ? "w-64" : "w-full"
      )}
      role="listbox"
    >
      {suggestions.map((suggestion, idx) => (
        <button
          key={suggestion.userId}
          role="option"
          aria-selected={idx === selectedIndex}
          onClick={() => onSelect(suggestion)}
          className={clsx(
            "w-full flex items-center justify-between gap-3 px-3 py-2 text-left transition",
            idx === selectedIndex
              ? "bg-blue-100 hover:bg-blue-200"
              : "hover:bg-gray-100"
          )}
          type="button"
        >
          <div className="min-w-0 flex items-center gap-2">
            <UserAvatar
              userId={suggestion.userId}
              avatar={suggestion.avatarUrl}
              size={32}
            />

            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-medium text-sm text-gray-900 truncate">
                  {suggestion.displayName}
                </span>
                <PresenceDot status={getUserStatus(suggestion.userId)} />
              </div>
            </div>
          </div>

          <div className="shrink-0 text-xs text-gray-500 truncate">
            {buildRightMentionLabel(suggestion)}
          </div>
        </button>
      ))}
    </div>
  );
}
