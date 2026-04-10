import type { Room } from "../../types/room";

interface GroupRoomItemProps {
  room: Room;
  isActive: boolean;
  onClick: () => void;
}

export default function GroupRoomItem({
  room,
  isActive,
  onClick,
}: GroupRoomItemProps) {
  return (
    <div
      onClick={onClick}
      className={`flex items-center gap-3 px-3 py-2.5 cursor-pointer transition-colors duration-100 ${
        isActive
          ? "bg-blue-50 border-l-2 border-l-blue-500"
          : "border-l-2 border-l-transparent hover:bg-gray-50"
      }`}
    >
      {/* Avatar */}
      <div className="relative flex-shrink-0">
        <img
          src={room.avatarUrl || "/default-avatar.png"}
          alt={room.name}
          className="w-9 h-9 rounded-xl object-cover"
        />
        {room.unreadCount > 0 && (
          <div className="absolute -top-0.5 -right-0.5 bg-red-500 text-white text-[10px] leading-none rounded-full min-w-[15px] h-[15px] flex items-center justify-center font-bold px-1">
            {room.unreadCount > 99 ? "99+" : room.unreadCount}
          </div>
        )}
      </div>

      {/* Room Info */}
      <div className="flex-1 min-w-0">
        <div className={`text-sm truncate font-medium ${isActive ? "text-blue-700" : "text-gray-800"}`}>
          {room.name}
        </div>
        {room.lastMessage && (
          <div className="text-xs text-gray-400 truncate mt-0.5 leading-tight">
            <span className="font-medium text-gray-500">{room.lastMessage.senderName}:</span>{" "}
            {room.lastMessage.content}
          </div>
        )}
      </div>
    </div>
  );
}
