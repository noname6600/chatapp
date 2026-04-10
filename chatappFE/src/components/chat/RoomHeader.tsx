import { useEffect, useRef, useState } from "react";
import { ChevronDown, Users, Settings, LogOut, PanelRightClose, PanelRightOpen } from "lucide-react";
import type { Room } from "../../types/room";

interface RoomHeaderProps {
  room: Room | null;
  otherUserName?: string;
  onInvite?: () => void;
  onSettings?: () => void;
  onLeave?: () => void;
  membersOpen?: boolean;
  onToggleMembers?: () => void;
}

export default function RoomHeader({
  room,
  otherUserName,
  onInvite,
  onSettings,
  onLeave,
  membersOpen,
  onToggleMembers,
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
        {onToggleMembers && (
          <button
            onClick={onToggleMembers}
            className="p-1.5 rounded-lg text-gray-500 hover:text-gray-800 hover:bg-gray-100 transition-colors"
            title={membersOpen ? "Hide members" : "Show members"}
          >
            {membersOpen ? <PanelRightClose size={18} /> : <PanelRightOpen size={18} />}
          </button>
        )}
        <img
          src={room.avatarUrl || "/default-avatar.png"}
          alt={displayName}
          className="w-8 h-8 rounded-full object-cover"
        />
      </div>
    </div>
  );
}
