import type { Room } from "../../types/room";

interface PrivateRoomItemProps {
  room: Room;
  isActive: boolean;
  onClick: () => void;
}

export default function PrivateRoomItem({
  room,
  isActive,
  onClick,
}: PrivateRoomItemProps) {
  return (
    <div
      onClick={onClick}
      className={`p-3 border-b cursor-pointer transition ${
        isActive
          ? "bg-blue-50 border-l-4 border-l-blue-500"
          : "hover:bg-gray-50 border-b border-gray-100"
      }`}
    >
      <div className="flex items-center gap-3">
        {/* Avatar */}
        <img
          src={room.avatarUrl || "/default-avatar.png"}
          alt={room.name}
          className="w-10 h-10 rounded-full object-cover flex-shrink-0"
        />

        {/* Room Info */}
        <div className="flex-1 min-w-0">
          <div className="font-medium text-sm text-gray-900 truncate">
            {room.name}
          </div>
          {room.lastMessage && (
            <div className="text-xs text-gray-500 truncate mt-0.5">
              {room.lastMessage.senderName}: {room.lastMessage.content}
            </div>
          )}
        </div>

        {/* Unread Badge */}
        {room.unreadCount > 0 && (
          <div className="flex-shrink-0 bg-red-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center font-semibold">
            {room.unreadCount > 9 ? "9+" : room.unreadCount}
          </div>
        )}
      </div>
    </div>
  );
}
