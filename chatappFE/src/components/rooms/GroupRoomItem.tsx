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
    <div className="relative group flex-shrink-0">
      {/* Active Indicator */}
      {isActive && (
        <div className="absolute -left-3 top-0 bottom-0 w-1 bg-blue-500 rounded-r" />
      )}

      {/* Room Avatar */}
      <div className="relative">
        <button
          onClick={onClick}
          className={`w-12 h-12 rounded-lg object-cover transition flex items-center justify-center flex-shrink-0 ${
            isActive ? "ring-2 ring-blue-500" : "hover:opacity-80"
          }`}
          title={room.name}
        >
          <img
            src={room.avatarUrl || "/default-avatar.png"}
            alt={room.name}
            className="w-full h-full rounded-lg object-cover"
          />
        </button>

        {/* Unread Indicator */}
        {room.unreadCount > 0 && (
          <div className="absolute -bottom-1 -right-1 bg-red-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center font-semibold">
            {room.unreadCount > 9 ? "9+" : room.unreadCount}
          </div>
        )}
      </div>

      {/* Hover Name Display - Fancy Tooltip */}
      <div className="absolute left-full ml-3 top-1/2 -translate-y-1/2 pointer-events-none opacity-0 group-hover:opacity-100 transition-all duration-200 z-50 group-hover:scale-100 scale-95">
        <div className="bg-gradient-to-r from-gray-900 to-gray-800 text-white text-sm rounded-lg px-3 py-2 whitespace-nowrap shadow-xl border border-gray-700">
          <div className="font-semibold">{room.name}</div>
          {/* Arrow */}
          <div className="absolute right-full top-1/2 -translate-y-1/2 w-0 h-0 border-t-6 border-b-6 border-r-6 border-t-transparent border-b-transparent border-r-gray-900 -mr-px" />
        </div>
      </div>
    </div>
  );
}
