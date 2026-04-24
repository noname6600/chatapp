import type { Room } from "../../types/room";
import { usePresenceStore } from "../../store/presence.store";
import UserAvatar from "../user/UserAvatar";

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
  const status = usePresenceStore((s) =>
    room.otherUserId ? s.getUserStatus(room.otherUserId) : "OFFLINE"
  );

  return (
    <button
      type="button"
      onClick={onClick}
      className={`group flex w-full items-center gap-3 rounded-[18px] border px-3 py-3 text-left transition-all duration-150 ${
        isActive
          ? "border-blue-200 bg-blue-50 shadow-sm shadow-blue-100/80"
          : "border-transparent bg-white/70 hover:border-slate-200 hover:bg-white hover:shadow-sm"
      }`}
    >
      <div className="relative flex-shrink-0">
        {room.otherUserId ? (
          <UserAvatar
            userId={room.otherUserId}
            avatar={room.avatarUrl}
            size={36}
            status={status}
          />
        ) : (
          <img
            src={room.avatarUrl || "/default-avatar.png"}
            alt={room.name}
            className="h-10 w-10 rounded-full object-cover ring-1 ring-slate-200/80"
          />
        )}
        {room.unreadCount > 0 && (
          <div className="absolute -right-1 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold leading-none text-white shadow-sm">
            {room.unreadCount > 99 ? "99+" : room.unreadCount}
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0">
        <div
          className={`truncate text-sm font-semibold ${
            isActive ? "text-blue-700" : "text-slate-800"
          }`}
        >
          {room.name}
        </div>
        {room.lastMessage && (
          <div className="mt-1 truncate text-xs leading-tight text-slate-400">
            <span className="font-medium text-slate-500">
              {room.lastMessage.senderName}:
            </span>{" "}
            {room.lastMessage.content}
          </div>
        )}
      </div>
    </button>
  );
}
