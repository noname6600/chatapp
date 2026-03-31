import { useCallback, useMemo, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { useUserStore } from "../../store/user.store";
import { useRooms } from "../../store/room.store";
import { startPrivateChatApi } from "../../api/room.service";
import CreateRoomModal from "./CreateRoomModal";
import JoinRoomModal from "./JoinRoomModal";

interface ConversationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectRoom?: (roomId: string) => void;
}

export default function ConversationModal({
  isOpen,
  onClose,
  onSelectRoom,
}: ConversationModalProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showJoinModal, setShowJoinModal] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const users = useUserStore((s) => s.users);
  const { loadRooms } = useRooms();

  // Filter users based on @mention search
  const filteredUsers = useMemo(() => {
    if (!searchQuery.startsWith("@")) return [];

    const query = searchQuery.slice(1).toLowerCase();
    if (!query) return [];

    // Get all friends and users in same groups
    const availableUsers = Object.values(users).filter((user) => {
      const matchesQuery =
        user.displayName.toLowerCase().startsWith(query) ||
        user.username.toLowerCase().startsWith(query);

      return matchesQuery;
    });

    return availableUsers;
  }, [searchQuery, users]);

  const handleSelectUser = useCallback(
    async (userId: string) => {
      setLoading(true);
      setError(null);

      try {
        const room = await startPrivateChatApi(userId);
        setSearchQuery("");
        await loadRooms();
        onSelectRoom?.(room.id);
        onClose();
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to start conversation"
        );
      } finally {
        setLoading(false);
      }
    },
    [loadRooms, onSelectRoom, onClose]
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <>
      {/* Modal Backdrop */}
      <div
        className="fixed inset-0 bg-black/50 z-40"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="fixed inset-0 flex items-center justify-center z-50 p-4">
        <div
          className="bg-white rounded-lg shadow-lg w-full max-w-md"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-4 border-b">
            <h2 className="text-lg font-semibold text-gray-900">
              Find or start a conversation
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
            {/* Create/Join Buttons */}
            <div className="flex gap-2">
              <button
                onClick={() => {
                  setShowCreateModal(true);
                }}
                className="flex-1 px-4 py-2 bg-blue-500 text-white rounded font-medium hover:bg-blue-600 transition text-sm"
              >
                Create Group
              </button>
              <button
                onClick={() => {
                  setShowJoinModal(true);
                }}
                className="flex-1 px-4 py-2 bg-green-500 text-white rounded font-medium hover:bg-green-600 transition text-sm"
              >
                Join Room
              </button>
            </div>

            {/* Search Bar */}
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">
                Direct Message
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
                    onKeyDown={handleKeyDown}
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
                <div className="border border-gray-200 rounded bg-gray-50 max-h-64 overflow-y-auto">
                  {filteredUsers.length === 0 ? (
                    <div className="p-3 text-sm text-gray-500 text-center">
                      {searchQuery.length > 1
                        ? "No members found"
                        : "Start typing..."}
                    </div>
                  ) : (
                    filteredUsers.map((user) => (
                      <button
                        key={user.accountId}
                        onClick={() => handleSelectUser(user.accountId)}
                        disabled={loading}
                        className="w-full flex items-center gap-2 px-3 py-2 hover:bg-white transition text-left border-b last:border-b-0 disabled:opacity-50"
                      >
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
                      </button>
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
          </div>
        </div>
      </div>

      {/* Nested Modals */}
      <CreateRoomModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSuccess={(roomId) => {
          onSelectRoom?.(roomId);
          onClose();
        }}
      />
      <JoinRoomModal
        isOpen={showJoinModal}
        onClose={() => setShowJoinModal(false)}
        onSuccess={(roomId) => {
          onSelectRoom?.(roomId);
          onClose();
        }}
      />
    </>
  );
}
