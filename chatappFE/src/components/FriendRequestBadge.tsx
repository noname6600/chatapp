import { useNavigate } from "react-router-dom";
import { useFriendStore } from "../store/friend.store";

export function FriendRequestBadge() {
  const navigate = useNavigate();
  const unreadCount = useFriendStore((state) => state.unreadFriendRequestCount);

  // Don't render if no unread requests
  if (unreadCount <= 0) {
    return null;
  }

  const handleClick = () => {
    navigate("/friends?tab=pending");
  };

  return (
    <button
      onClick={handleClick}
      className="relative inline-flex items-center justify-center"
      aria-label={`${unreadCount} unread friend requests`}
      title={`${unreadCount} unread friend request${unreadCount !== 1 ? "s" : ""}`}
    >
      {/* Badge circle with count */}
      <span className="absolute top-0 right-0 inline-flex items-center justify-center px-2 py-1 text-xs font-bold leading-none text-white transform translate-x-1/2 -translate-y-1/2 bg-red-600 rounded-full min-w-fit">
        {unreadCount > 99 ? "99+" : unreadCount}
      </span>

      {/* Friend/People icon */}
      <svg
        className="w-6 h-6 text-gray-700 dark:text-gray-300 hover:text-blue-500 transition-colors"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M12 4.354a4 4 0 110 5.292M15 12H9m3 9a9 9 0 11.001-18A9 9 0 0118 21z"
        />
      </svg>
    </button>
  );
}
