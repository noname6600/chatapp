import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Search, X, Copy, Check } from "lucide-react";
import { useUserStore } from "../../store/user.store";
import { getRoomCode, getRoomMembers } from "../../api/room.service";
import { getFriendsApi } from "../../api/friend.service";
import { searchUserByUsernameApi } from "../../api/user.service";
import type { Room } from "../../types/room";
import type { UserProfile } from "../../types/user";
import {
  shouldApplyRoomCodeResponse,
  upsertRoomCodeByRoom,
} from "../../utils/roomCodeIntegrity";

const VISIBLE_LIMIT = 10;

interface InviteMembersModalProps {
  isOpen: boolean;
  room: Room;
  onClose: () => void;
  onInviteUser: (userId: string) => Promise<void>;
}

export default function InviteMembersModal({
  isOpen,
  room,
  onClose,
  onInviteUser,
}: InviteMembersModalProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [invitingUserId, setInvitingUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [roomCodesByRoom, setRoomCodesByRoom] = useState<Record<string, string>>({});
  const [loadingRoomCode, setLoadingRoomCode] = useState(false);
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [copiedCode, setCopiedCode] = useState(false);
  const [copyError, setCopyError] = useState<string | null>(null);
  const [roomMemberIds, setRoomMemberIds] = useState<string[]>([]);
  const [friendIds, setFriendIds] = useState<string[]>([]);
  const [searchResults, setSearchResults] = useState<UserProfile[] | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [inviteSuccessByUserId, setInviteSuccessByUserId] = useState<Record<string, boolean>>({});

  const searchInputRef = useRef<HTMLInputElement>(null);
  const roomCodeRequestToken = useRef(0);
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchUsers = useUserStore((s) => s.fetchUsers);
  const cacheUsers = useUserStore((s) => s.cacheUsers);
  const users = useUserStore((s) => s.users);
  const roomCode = roomCodesByRoom[room.id] ?? null;

  useEffect(() => {
    if (!isOpen) return;
    setSearchQuery("");
    setSearchResults(null);
    setError(null);
    setCopyError(null);
    setCopiedCode(false);
  }, [isOpen, room.id]);

  // Load room code on open
  useEffect(() => {
    if (!isOpen || roomCode) return;

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

        if (!canApply) return;

        setRoomCodesByRoom((prev) => upsertRoomCodeByRoom(prev, requestRoomId, code));
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

  // Load room members + friends on open
  useEffect(() => {
    if (!isOpen) return;

    let alive = true;

    const loadCandidates = async () => {
      try {
        setLoadingCandidates(true);
        setError(null);

        const [members, ids] = await Promise.all([
          getRoomMembers(room.id),
          getFriendsApi(),
        ]);

        if (!alive) return;

        const memberIds = members.map((m) => m.userId);
        setRoomMemberIds(memberIds);
        setFriendIds(ids);

        // Pre-fetch friend profiles into cache (respects 1-day TTL)
        const nonMemberFriends = ids.filter((id) => !memberIds.includes(id));
        if (nonMemberFriends.length) void fetchUsers(nonMemberFriends);
      } catch (err) {
        if (!alive) return;
        setError(err instanceof Error ? err.message : "Failed to load invite candidates");
      } finally {
        if (alive) setLoadingCandidates(false);
      }
    };

    void loadCandidates();
    return () => { alive = false; };
  }, [fetchUsers, isOpen, room.id]);

  // Debounced search — fires API when query >= 2 chars
  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);

    const query = searchQuery.trim();
    if (query.length < 2) {
      setSearchResults(null);
      return;
    }

    searchDebounceRef.current = setTimeout(async () => {
      setIsSearching(true);
      try {
        const results = await searchUserByUsernameApi(query);
        cacheUsers(results);
        setSearchResults(results);
      } catch {
        setSearchResults([]);
      } finally {
        setIsSearching(false);
      }
    }, 350);

    return () => {
      if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    };
  }, [cacheUsers, searchQuery]);

  const memberSet = useMemo(() => new Set(roomMemberIds), [roomMemberIds]);

  // What to show: search results (when querying) or friends (when idle)
  const displayUsers = useMemo((): UserProfile[] => {
    const query = searchQuery.trim();

    if (query.length >= 2) {
      return (searchResults ?? [])
        .filter((u) => u.accountId && !memberSet.has(u.accountId))
        .slice(0, VISIBLE_LIMIT);
    }

    return friendIds
      .filter((id) => !memberSet.has(id) && users[id])
      .map((id) => users[id])
      .filter((u): u is UserProfile => !!u)
      .slice(0, VISIBLE_LIMIT);
  }, [friendIds, memberSet, searchQuery, searchResults, users]);

  const handleInviteUser = useCallback(
    async (userId: string) => {
      setInvitingUserId(userId);
      setError(null);
      try {
        await onInviteUser(userId);
        setInviteSuccessByUserId((prev) => ({ ...prev, [userId]: true }));
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to send invite");
      } finally {
        setInvitingUserId(null);
      }
    },
    [onInviteUser]
  );

  const copyRoomCode = useCallback(async () => {
    if (!roomCode) return;
    try {
      setCopyError(null);
      await navigator.clipboard.writeText(roomCode);
      setCopiedCode(true);
      setTimeout(() => setCopiedCode(false), 2000);
    } catch {
      setCopyError("Unable to copy room code");
    }
  }, [roomCode]);

  const query = searchQuery.trim();
  const isLoading = loadingCandidates || (query.length >= 2 && isSearching);
  const showSearchHint = !loadingCandidates && query.length < 2 && friendIds.length === 0;

  if (!isOpen) return null;

  return (
    <>
      <div className="fixed inset-0 bg-black/50 z-40" onClick={onClose} />

      <div className="fixed inset-0 flex items-center justify-center z-50 p-4">
        <div
          className="bg-white rounded-lg shadow-lg w-full max-w-md"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-4 border-b">
            <h2 className="text-lg font-semibold text-gray-900">Invite Members</h2>
            <button onClick={onClose} className="p-1 text-gray-400 hover:text-gray-600 transition">
              <X size={20} />
            </button>
          </div>

          {/* Content */}
          <div className="p-4 space-y-4 max-h-96 overflow-y-auto">
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">
                Search Members
              </label>

              {/* Search Bar */}
              <div className="relative flex items-center">
                <Search size={16} className="absolute left-3 text-gray-400 pointer-events-none" />
                <input
                  ref={searchInputRef}
                  type="text"
                  placeholder="Search by name or @username..."
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

              {/* User List */}
              <div className="border border-gray-200 rounded bg-gray-50">
                {isLoading ? (
                  <div className="p-3 text-sm text-gray-500 text-center">
                    {loadingCandidates ? "Loading..." : "Searching..."}
                  </div>
                ) : showSearchHint ? (
                  <div className="p-3 text-sm text-gray-400 text-center">
                    Type to search for people to invite
                  </div>
                ) : displayUsers.length === 0 ? (
                  <div className="p-3 text-sm text-gray-500 text-center">
                    {query.length >= 2
                      ? "No users found — try a different name"
                      : "No friends available to invite"}
                  </div>
                ) : (
                  <>
                    {displayUsers.map((user) => (
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
                            <div className="flex items-center gap-2 min-w-0">
                              <div className="text-sm font-medium text-gray-900 truncate">
                                {user.displayName}
                              </div>
                              <div className="text-xs text-gray-500 truncate">@{user.username}</div>
                            </div>
                            <div className="text-xs text-gray-500 truncate">
                              {user.aboutMe?.trim() || "No about text"}
                            </div>
                          </div>
                        </div>

                        <button
                          onClick={() => handleInviteUser(user.accountId)}
                          disabled={invitingUserId === user.accountId}
                          className="ml-2 px-3 py-1 bg-blue-500 text-white text-xs rounded hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition flex-shrink-0"
                        >
                          {invitingUserId === user.accountId
                            ? "Sending..."
                            : inviteSuccessByUserId[user.accountId]
                            ? "Sent"
                            : "Invite"}
                        </button>
                      </div>
                    ))}

                    {/* Hint when there are exactly 10 results */}
                    {displayUsers.length === VISIBLE_LIMIT && (
                      <div className="px-3 py-2 text-xs text-gray-400 text-center border-t">
                        Showing top 10 — type to narrow results
                      </div>
                    )}
                  </>
                )}
              </div>

              {error && (
                <div className="p-2 bg-red-50 border border-red-200 rounded text-sm text-red-700">
                  {error}
                </div>
              )}
              {copyError && (
                <div className="p-2 bg-red-50 border border-red-200 rounded text-sm text-red-700">
                  {copyError}
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
                  onClick={() => void copyRoomCode()}
                  disabled={!roomCode}
                  aria-label="Copy room code"
                  className="px-3 py-2 bg-gray-100 text-gray-900 rounded border border-gray-200 hover:bg-gray-200 transition flex items-center gap-2 disabled:opacity-50"
                >
                  {copiedCode ? <Check size={16} className="text-green-600" /> : <Copy size={16} />}
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
