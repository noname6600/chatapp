import { useEffect, useRef, useState } from "react";
import { ChevronDown, Users, Settings, LogOut, PanelRightClose, PanelRightOpen, Pin } from "lucide-react";
import type { Room } from "../../types/room";
import { usePresenceStore } from "../../store/presence.store";
import UserAvatar from "../user/UserAvatar";

interface RoomHeaderProps {
  room: Room | null;
  otherUserName?: string;
  onInvite?: () => void;
  onSettings?: () => void;
  onLeave?: () => void;
  membersOpen?: boolean;
  onToggleMembers?: () => void;
  onTogglePins?: () => void;
  pinsOpen?: boolean;
  pinCount?: number;
}

export default function RoomHeader({
  room,
  otherUserName,
  onInvite,
  onSettings,
  onLeave,
  membersOpen,
  onToggleMembers,
  onTogglePins,
  pinsOpen,
  pinCount = 0,
}: RoomHeaderProps) {
  const [showDropdown, setShowDropdown] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node)
      ) {
        setShowDropdown(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  if (!room) return null;

  const isGroupRoom = room.type === "GROUP";
  const displayName = isGroupRoom ? room.name : otherUserName || room.name;
  const otherUserId = room.otherUserId ?? null;
  const otherStatus = usePresenceStore((s) =>
    otherUserId ? s.getUserStatus(otherUserId) : "OFFLINE"
  );

  return (
    <div className="border-b bg-white px-4 py-3 flex items-center justify-between">
      {/* Left: Room Name */}
      {isGroupRoom ? (
        <div className="relative" ref={dropdownRef}>
          <button
            onClick={() => setShowDropdown(!showDropdown)}
            className="flex items-center gap-2 font-semibold text-gray-900 hover:text-blue-600 transition"
          >
            <span>{displayName}</span>
            <ChevronDown size={18} />
          </button>

          {/* Dropdown Menu */}
          {showDropdown && (
            <div className="absolute top-full left-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-30 w-48">
              <button
                onClick={() => {
                  onInvite?.();
                  setShowDropdown(false);
                }}
                className="w-full flex items-center gap-2 px-4 py-2 text-left text-gray-900 hover:bg-gray-50 transition border-b"
              >
                <Users size={18} />
                Invite Members
              </button>

              <button
                onClick={() => {
                  onSettings?.();
                  setShowDropdown(false);
                }}
                className="w-full flex items-center gap-2 px-4 py-2 text-left text-gray-900 hover:bg-gray-50 transition border-b"
              >
                <Settings size={18} />
                Settings
              </button>

              <button
                onClick={() => {
                  onLeave?.();
                  setShowDropdown(false);
                }}
                className="w-full flex items-center gap-2 px-4 py-2 text-left text-red-600 hover:bg-red-50 transition"
              >
                <LogOut size={18} />
                Leave Group
              </button>
            </div>
          )}
        </div>
      ) : (
        <div className="font-semibold text-gray-900">{displayName}</div>
      )}

      {/* Right: Avatar + Members Toggle */}
      <div className="flex items-center gap-2">
        {onTogglePins && (
          <button
            onClick={onTogglePins}
            className={`relative p-1.5 rounded-lg transition-colors ${pinsOpen ? "text-blue-700 bg-blue-50" : "text-gray-500 hover:text-gray-800 hover:bg-gray-100"}`}
            title={pinsOpen ? "Hide pinned messages" : "Show pinned messages"}
            aria-label={pinsOpen ? "Hide pinned messages" : "Show pinned messages"}
          >
            <Pin size={18} />
            {pinCount > 0 && (
              <span className="absolute -top-1 -right-1 min-w-4 h-4 px-1 rounded-full bg-blue-600 text-white text-[10px] leading-4 text-center font-semibold">
                {pinCount > 99 ? "99+" : pinCount}
              </span>
            )}
          </button>
        )}
        {onToggleMembers && (
          <button
            onClick={onToggleMembers}
            className="p-1.5 rounded-lg text-gray-500 hover:text-gray-800 hover:bg-gray-100 transition-colors"
            title={membersOpen ? "Hide members" : "Show members"}
          >
            {membersOpen ? <PanelRightClose size={18} /> : <PanelRightOpen size={18} />}
          </button>
        )}
        {isGroupRoom ? (
          <img
            src={room.avatarUrl || "/default-avatar.png"}
            alt={displayName}
            className="w-8 h-8 rounded-full object-cover"
          />
        ) : otherUserId ? (
          <UserAvatar userId={otherUserId} avatar={room.avatarUrl} size={32} status={otherStatus} />
        ) : (
          <img
            src={room.avatarUrl || "/default-avatar.png"}
            alt={displayName}
            className="w-8 h-8 rounded-full object-cover"
          />
        )}
      </div>
    </div>
  );
}
