import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Search, X, Copy, Check } from "lucide-react";
import { useUserStore } from "../../store/user.store";
import { inviteMemberApi, getRoomCode } from "../../api/room.service";
import type { Room } from "../../types/room";
import {
  shouldApplyRoomCodeResponse,
  upsertRoomCodeByRoom,
} from "../../utils/roomCodeIntegrity";

interface InviteMembersModalProps {
  isOpen: boolean;
  room: Room;
  onClose: () => void;
}

export default function InviteMembersModal({
  isOpen,
  room,
  onClose,
}: InviteMembersModalProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [invitingUserId, setInvitingUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [roomCodesByRoom, setRoomCodesByRoom] = useState<
    Record<string, string>
  >({});
  const [loadingRoomCode, setLoadingRoomCode] = useState(false);
  const [copiedCode, setCopiedCode] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const roomCodeRequestToken = useRef(0);

  const users = useUserStore((s) => s.users);
  const roomCode = roomCodesByRoom[room.id] ?? null;

  // Load room code on open
  useEffect(() => {
    if (!isOpen || roomCode) {
      return;
    }

    const requestToken = ++roomCodeRequestToken.current;
    const requestRoomId = room.id;

    const loadCode = async () => {
      try {
        setLoadingRoomCode(true);
        const code = await getRoomCode(requestRoomId);

        const canApply = shouldApplyRoomCodeResponse({
          requestToken,
          latestToken: roomCodeRequestToken.current,
          requestRoomId,
          activeRoomId: room.id,
        });

        if (!canApply) {
          // Non-blocking trace to understand race scenarios during fast room switching.
          console.debug(
            "[room-code] stale response ignored",
            { requestRoomId, activeRoomId: room.id, requestToken }
          );
          return;
        }

        setRoomCodesByRoom((prev) =>
          upsertRoomCodeByRoom(prev, requestRoomId, code)
        );
      } catch (err) {
        console.error("Failed to load room code:", err);
      } finally {
        if (requestToken === roomCodeRequestToken.current) {
          setLoadingRoomCode(false);
        }
      }
    };

    loadCode();
  }, [isOpen, room.id, roomCode]);

  // Filter users - friends or users in same group
  const filteredUsers = useMemo(() => {
    if (!searchQuery.startsWith("@")) return [];

    const query = searchQuery.slice(1).toLowerCase();
    if (!query) return [];

    return Object.values(users).filter(
      (user) =>
        (user.displayName.toLowerCase().startsWith(query) ||
          user.username.toLowerCase().startsWith(query))
    );
  }, [searchQuery, users]);

  const handleInviteUser = useCallback(
    async (userId: string) => {
      setInvitingUserId(userId);
      setError(null);

      try {
        await inviteMemberApi(room.id, userId);
        setSearchQuery("");
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to invite member"
        );
      } finally {
        setInvitingUserId(null);
      }
    },
    [room.id]
  );

  const copyRoomCode = useCallback(() => {
    if (roomCode) {
      navigator.clipboard.writeText(roomCode);
      setCopiedCode(true);
      setTimeout(() => setCopiedCode(false), 2000);
    }
  }, [roomCode]);

  if (!isOpen) return null;

  return (
    <>
      {/* Modal Backdrop */}
      <div className="fixed inset-0 bg-black/50 z-40" onClick={onClose} />

      {/* Modal */}
      <div className="fixed inset-0 flex items-center justify-center z-50 p-4">
        <div
          className="bg-white rounded-lg shadow-lg w-full max-w-md"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-4 border-b">
            <h2 className="text-lg font-semibold text-gray-900">
              Invite Members
            </h2>
            <button
              onClick={onClose}
              className="p-1 text-gray-400 hover:text-gray-600 transition"
            >
              <X size={20} />
            </button>
          </div>

          {/* Content */}
          <div className="p-4 space-y-4 max-h-96 overflow-y-auto">
            {/* Search Bar */}
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">
                Search Members
              </label>
              <div className="relative">
                <div className="relative flex items-center">
                  <Search
                    size={16}
                    className="absolute left-3 text-gray-400 pointer-events-none"
                  />
                  <input
                    ref={searchInputRef}
                    type="text"
                    placeholder="Type @ to mention a member..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-8 pr-3 py-2 bg-gray-100 text-sm rounded border border-gray-200 focus:border-blue-500 focus:outline-none"
                    autoFocus
                  />
                  {searchQuery && (
                    <button
                      onClick={() => setSearchQuery("")}
                      className="absolute right-3 text-gray-400 hover:text-gray-600"
                    >
                      <X size={16} />
                    </button>
                  )}
                </div>
              </div>

              {/* User Suggestions */}
              {searchQuery.startsWith("@") && (
                <div className="border border-gray-200 rounded bg-gray-50">
                  {filteredUsers.length === 0 ? (
                    <div className="p-3 text-sm text-gray-500 text-center">
                      {searchQuery.length > 1
                        ? "No members found"
                        : "Start typing..."}
                    </div>
                  ) : (
                    filteredUsers.map((user) => (
                      <div
                        key={user.accountId}
                        className="flex items-center justify-between px-3 py-2 hover:bg-white transition border-b last:border-b-0"
                      >
                        <div className="flex items-center gap-2 flex-1 min-w-0">
                          <img
                            src={user.avatarUrl || "/default-avatar.png"}
                            alt={user.displayName}
                            className="w-8 h-8 rounded-full flex-shrink-0 object-cover"
                          />
                          <div className="flex-1 min-w-0">
                            <div className="text-sm font-medium text-gray-900 truncate">
                              {user.displayName}
                            </div>
                            <div className="text-xs text-gray-500">
                              @{user.username}
                            </div>
                          </div>
                        </div>

                        <button
                          onClick={() => handleInviteUser(user.accountId)}
                          disabled={invitingUserId === user.accountId}
                          className="ml-2 px-3 py-1 bg-blue-500 text-white text-xs rounded hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition flex-shrink-0"
                        >
                          {invitingUserId === user.accountId
                            ? "Inviting..."
                            : "Invite"}
                        </button>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* Error Message */}
              {error && (
                <div className="p-2 bg-red-50 border border-red-200 rounded text-sm text-red-700">
                  {error}
                </div>
              )}
            </div>

            {/* Room Code Section */}
            <div className="space-y-2 pt-4 border-t">
              <label className="block text-sm font-medium text-gray-700">
                Room Invite Code
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={roomCode || (loadingRoomCode ? "Loading..." : "N/A")}
                  readOnly
                  className="flex-1 px-3 py-2 bg-gray-100 text-sm rounded border border-gray-200 font-mono"
                />
                <button
                  onClick={copyRoomCode}
                  disabled={!roomCode}
                  className="px-3 py-2 bg-gray-100 text-gray-900 rounded border border-gray-200 hover:bg-gray-200 transition flex items-center gap-2 disabled:opacity-50"
                >
                  {copiedCode ? (
                    <Check size={16} className="text-green-600" />
                  ) : (
                    <Copy size={16} />
                  )}
                </button>
              </div>
              <p className="text-xs text-gray-500">
                Share this code with others to invite them to the group
              </p>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
