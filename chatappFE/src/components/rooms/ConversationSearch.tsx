import { useCallback, useMemo, useRef, useState } from "react";
import { Search, Plus, LogIn, X } from "lucide-react";
import { useUserStore } from "../../store/user.store";
import { useRooms } from "../../store/room.store";
import { startPrivateChatApi } from "../../api/room.service";
import CreateRoomModal from "./CreateRoomModal";
import JoinRoomModal from "./JoinRoomModal";

interface ConversationSearchProps {
  onSelectRoom?: (roomId: string) => void;
}

export default function ConversationSearch({
  onSelectRoom,
}: ConversationSearchProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showJoinModal, setShowJoinModal] = useState(false);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const users = useUserStore((s) => s.users);
  const { loadRooms } = useRooms();

  // Filter users based on search query (starts with @)
  const filteredUsers = useMemo(() => {
    if (!searchQuery.startsWith("@")) return [];

    const query = searchQuery.slice(1).toLowerCase();
    if (!query) return [];

    return Object.values(users).filter(
      (user) =>
        user.displayName.toLowerCase().includes(query) ||
        user.username.toLowerCase().includes(query)
    );
  }, [searchQuery, users]);

  const handleSelectUser = useCallback(
    async (userId: string) => {
      setLoading(true);
      setError(null);

      try {
        const room = await startPrivateChatApi(userId);
        setSearchQuery("");
        setShowSuggestions(false);
        await loadRooms();
        onSelectRoom?.(room.id);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to start conversation"
        );
      } finally {
        setLoading(false);
      }
    },
    [loadRooms, onSelectRoom]
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      setShowSuggestions(false);
      setSearchQuery("");
    }
  };

  return (
    <div className="border-b">
      {/* Header */}
      <div className="p-4 pb-3">
        <h2 className="text-lg font-semibold text-gray-900 mb-3">
          Find or start a conversation
        </h2>

        {/* Create/Join Buttons */}
        <div className="flex gap-2 mb-3">
          <button
            onClick={() => setShowCreateModal(true)}
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-blue-500 text-white rounded text-sm hover:bg-blue-600 transition font-medium"
          >
            <Plus size={16} />
            Create
          </button>
          <button
            onClick={() => setShowJoinModal(true)}
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-green-500 text-white rounded text-sm hover:bg-green-600 transition font-medium"
          >
            <LogIn size={16} />
            Join
          </button>
        </div>

        {/* Search Bar */}
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
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setShowSuggestions(e.target.value.startsWith("@"));
              }}
              onFocus={() => {
                if (searchQuery.startsWith("@")) {
                  setShowSuggestions(true);
                }
              }}
              onKeyDown={handleKeyDown}
              className="w-full pl-8 pr-3 py-2 bg-gray-100 text-sm rounded border border-gray-200 focus:border-blue-500 focus:outline-none"
            />
            {searchQuery && (
              <button
                onClick={() => {
                  setSearchQuery("");
                  setShowSuggestions(false);
                }}
                className="absolute right-3 text-gray-400 hover:text-gray-600"
              >
                <X size={16} />
              </button>
            )}
          </div>

          {/* Suggestions Dropdown */}
          {showSuggestions && searchQuery.startsWith("@") && (
            <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded shadow-lg z-10 max-h-64 overflow-y-auto">
              {filteredUsers.length === 0 ? (
                <div className="p-3 text-sm text-gray-500 text-center">
                  {searchQuery.length > 1 ? "No members found" : "Start typing..."}
                </div>
              ) : (
                filteredUsers.map((user) => (
                  <button
                    key={user.accountId}
                    onClick={() => handleSelectUser(user.accountId)}
                    disabled={loading}
                    className="w-full flex items-center gap-2 px-3 py-2 hover:bg-gray-100 transition text-left border-b last:border-b-0 disabled:opacity-50"
                  >
                    <img
                      src={user.avatarUrl || "/default-avatar.png"}
                      alt={user.displayName}
                      className="w-8 h-8 rounded-full flex-shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-900 truncate">
                        {user.displayName}
                      </div>
                      <div className="text-xs text-gray-500">@{user.username}</div>
                    </div>
                  </button>
                ))
              )}
            </div>
          )}

          {/* Error Message */}
          {error && (
            <div className="mt-2 p-2 bg-red-50 border border-red-200 rounded text-sm text-red-700">
              {error}
            </div>
          )}
        </div>
      </div>

      <CreateRoomModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSuccess={onSelectRoom}
      />
      <JoinRoomModal
        isOpen={showJoinModal}
        onClose={() => setShowJoinModal(false)}
        onSuccess={onSelectRoom}
      />
    </div>
  );
}
