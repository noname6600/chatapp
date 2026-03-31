import React from 'react';
import { ChevronDown } from 'lucide-react';

interface UnreadMessageIndicatorProps {
  unreadCount: number;
  onJumpToLatest: () => void;
  label?: string;
  jumpLabel?: string;
}

/**
 * UnreadMessageIndicator is a sticky banner displayed above the message list
 * when there are unread messages. It shows the unread count and provides a
 * "Jump to Latest" button to scroll to the most recent message.
 *
 * The banner only renders when unreadCount > 0.
 */
export const UnreadMessageIndicator: React.FC<UnreadMessageIndicatorProps> = ({
  unreadCount,
  onJumpToLatest,
  label,
  jumpLabel,
}) => {
  // Show banner when either unread-count mode or custom label mode is active.
  if (unreadCount <= 0 && !label) {
    return null;
  }

  const handleJumpClick = () => {
    onJumpToLatest();
  };

  return (
    <div
      className="sticky top-0 z-10 bg-blue-50 border-b border-blue-200 px-4 py-3 flex items-center justify-between shadow-sm"
      aria-label="Unread messages"
      role="status"
    >
      <span className="text-sm font-medium text-blue-900">
        {label ?? (unreadCount === 1 ? '1 unread message' : `${unreadCount} unread messages`)}
      </span>
      <button
        onClick={handleJumpClick}
        aria-label="Jump to latest messages"
        className="flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-sm font-medium rounded hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-1 active:bg-blue-800 transition-colors"
      >
        <span>{jumpLabel ?? "Jump to Latest"}</span>
        <ChevronDown className="w-4 h-4" />
      </button>
    </div>
  );
};
