import clsx from "clsx";
import { Tooltip } from "../ui/Tooltip";
import type { Reaction } from "../../types/message";

interface ReactionGroupProps {
  reactions?: Reaction[];
  onReactionClick?: (emoji: string) => void;
  onAddReaction?: () => void;
  disabled?: boolean;
  className?: string;
  currentUserId?: string | null;
}

export default function ReactionGroup({
  reactions = [],
  onReactionClick,
  onAddReaction,
  disabled = false,
  className,
  currentUserId: _currentUserId,
}: ReactionGroupProps) {
  if (reactions.length === 0) {
    return null;
  }

  return (
    <div className={clsx("flex flex-wrap gap-1 mt-2", className)}>
      {reactions.map((reaction) => (
        <Tooltip
          key={reaction.emoji}
          content={`${reaction.count} reaction${reaction.count > 1 ? "s" : ""}`}
        >
          <button
            onClick={() => onReactionClick?.(reaction.emoji)}
            disabled={disabled}
            className={clsx(
              "inline-flex items-center gap-1 px-2 py-1 rounded-full text-sm transition",
              "border border-gray-300 hover:bg-gray-100 active:bg-gray-200",
              reaction.reactedByMe
                ? "user-reaction bg-gradient-to-r from-blue-200 to-blue-100 border-blue-400 border-2 text-blue-700 font-bold shadow-md hover:shadow-lg hover:from-blue-300 hover:to-blue-200 animate-pulse-subtle"
                : "",
              "disabled:opacity-50 disabled:cursor-not-allowed"
            )}
            style={
              reaction.reactedByMe
                ? ({
                    "--reaction-user-highlight-bg": "var(--blue-200, rgb(191, 219, 254))",
                  } as React.CSSProperties)
                : undefined
            }
            type="button"
          >
            <span>{reaction.emoji}</span>
            <span className="text-xs font-medium">{reaction.count}</span>
            {reaction.reactedByMe && (
              <span className="text-xs ml-0.5" title="Your reaction">
                ✓
              </span>
            )}
          </button>
        </Tooltip>
      ))}

      {onAddReaction && (
        <button
          onClick={onAddReaction}
          disabled={disabled}
          className={clsx(
            "px-2 py-1 rounded-full text-sm transition",
            "border border-gray-300 hover:bg-gray-100 active:bg-gray-200",
            "disabled:opacity-50 disabled:cursor-not-allowed"
          )}
          title="Add reaction"
          type="button"
        >
          +
        </button>
      )}
    </div>
  );
}
