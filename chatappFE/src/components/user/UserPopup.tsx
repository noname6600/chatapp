import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useUserOverlay } from "../../store/userOverlay.store";
import { useUserStore } from "../../store/user.store";
import { useFriendStore } from "../../store/friend.store";
import { useAuth } from "../../store/auth.store";
import { useChat } from "../../store/chat.store";
import { useRooms } from "../../store/room.store";

import {
  sendFriendRequestApi,
  unfriendApi,
  blockUserApi,
} from "../../api/friend.service";

import { startPrivateChatApi } from "../../api/room.service";
import { getUserByIdApi } from "../../api/user.service";
import { resolveProfilePresentation } from "../../utils/profilePresentation";
import ProfileIdentityCard from "../profile/ProfileIdentityCard";

type FriendshipStatus =
  | "NONE"
  | "FRIENDS"
  | "REQUEST_SENT"
  | "REQUEST_RECEIVED"
  | "BLOCKED_BY_ME"
  | "BLOCKED_ME";

export default function UserPopup() {
  const { userId, rect, source, close } = useUserOverlay();
  const navigate = useNavigate();
  const { userId: myId } = useAuth();
  const { setActiveRoom, sendMessage } = useChat();
  const { roomsById } = useRooms();

  const user = useUserStore((s) =>
    userId ? s.users[userId] : undefined
  );
  const fetchUsers = useUserStore((s) => s.fetchUsers);
  const updateUserLocal = useUserStore((s) => s.updateUserLocal);

  const status = useFriendStore((s) =>
    userId ? (s.map[userId] as FriendshipStatus | undefined) : undefined
  );
  const resolve = useFriendStore((s) => s.resolve);
  const setStatus = useFriendStore((s) => s.setStatus);

  const popupRef = useRef<HTMLDivElement>(null);

  const [loading, setLoading] = useState(false);
  const [hoverFriend, setHoverFriend] = useState(false);
  const [hoverMore, setHoverMore] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [hoverInviteToGroup, setHoverInviteToGroup] = useState(false);
  const [showRemove, setShowRemove] = useState(false);
  const [dmText, setDmText] = useState("");

  const isSelf = userId === myId;
  const isPending = status === "REQUEST_SENT";
  const presentation = resolveProfilePresentation(user ?? {}, { fallbackAbout: false });
  const inviteableGroups = Object.values(roomsById)
    .filter((room) => room.type === "GROUP")
    .sort((a, b) => a.name.localeCompare(b.name));

  /* ================= RESET WHEN SWITCH USER ================= */
  useEffect(() => {
    setMenuOpen(false);
    setHoverInviteToGroup(false);
    setShowRemove(false);
    setDmText("");
  }, [userId]);

  /* ================= LOAD USER + FRIEND STATUS ================= */
  useEffect(() => {
    if (!userId) return;

    if (!user) fetchUsers([userId]);
    if (!status && !isSelf) resolve(userId);
  }, [userId, user, status, fetchUsers, resolve, isSelf]);

  useEffect(() => {
    if (!userId) return;

    // Bulk user fetches can omit profile fields like about/background; hydrate on-demand.
    if (user?.aboutMe != null && user?.backgroundColor != null) return;

    let cancelled = false;
    void (async () => {
      try {
        const profile = await getUserByIdApi(userId);
        if (!cancelled) {
          updateUserLocal(profile);
        }
      } catch {
        // Keep existing cached user if detail hydration fails.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [userId, user?.aboutMe, user?.backgroundColor, updateUserLocal]);

  /* ================= CLICK OUTSIDE ================= */
  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (
        popupRef.current &&
        !popupRef.current.contains(e.target as Node)
      ) {
        close();
      }
    };

    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [close]);

  /* ================= ESC CLOSE ================= */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  if (!userId || !rect) return null;

  /* ================= SMART POSITION ================= */
  const width = 320;
  const height = 420;
  const margin = 12;

  const spaceRight = window.innerWidth - rect.right;
  const spaceLeft = rect.left;

  let left = rect.left;
  let top = rect.bottom + margin;

  if (source === "SIDEBAR") {
    left = rect.left - width - margin;

    if (left < margin) {
      left = rect.right + margin;
    }

    top = Math.min(rect.top, window.innerHeight - height - margin);
  } else {
    if (spaceRight > width + margin) {
      left = rect.right + margin;
    } else if (spaceLeft > width + margin) {
      left = rect.left - width - margin;
    } else {
      left = (window.innerWidth - width) / 2;
    }

    if (top + height > window.innerHeight) {
      top = window.innerHeight - height - margin;
    }
  }

  const style: React.CSSProperties = {
    position: "fixed",
    top,
    left,
    width,
    zIndex: 9999,
  };

  /* ================= HELPERS ================= */
  const runAsync = async (fn: () => Promise<void>) => {
    if (loading) return;

    try {
      setLoading(true);
      await fn();
    } finally {
      setLoading(false);
    }
  };

  const handleFriendClick = () => {
    if (!status || loading || isSelf) return;

    if (status === "NONE") {
      runAsync(async () => {
        await sendFriendRequestApi(userId);
        setStatus(userId, "REQUEST_SENT");
      });
    }

    if (status === "FRIENDS") {
      setShowRemove((v) => !v);
      setMenuOpen(false);
    }
  };

  /* ================= START PRIVATE CHAT ================= */
  const startMiniDM = async () => {
    const trimmed = dmText.trim();
    if (!trimmed || !userId || isSelf) return;

    await runAsync(async () => {
      const room = await startPrivateChatApi(userId);

      window.dispatchEvent(new Event("rooms:reload"));

      await setActiveRoom(room.id);

      await sendMessage(room.id, trimmed);

      navigate("/chat");

      setDmText("");
      close();
    });
  };

  const openProfileSettings = () => {
    navigate("/settings");
    close();
  };

  const sendGroupInvite = (groupRoomId: string) => {
    if (!userId || isSelf) {
      return;
    }

    const targetGroup = roomsById[groupRoomId];
    if (!targetGroup || targetGroup.type !== "GROUP") {
      return;
    }

    void runAsync(async () => {
      const room = await startPrivateChatApi(userId);

      window.dispatchEvent(new Event("rooms:reload"));

      await setActiveRoom(room.id);
      await sendMessage(room.id, "", [], null, [
        {
          type: "ROOM_INVITE",
          roomInvite: {
            roomId: targetGroup.id,
            roomName: targetGroup.name,
            roomAvatarUrl: targetGroup.avatarUrl ?? undefined,
          },
        },
      ]);

      navigate("/chat");
      setMenuOpen(false);
      close();
    });
  };

  const topActions = (
    <>
      {!isSelf && (
        <div className="relative">
          <button
            disabled={loading || isPending}
            onClick={handleFriendClick}
            onMouseEnter={() => setHoverFriend(true)}
            onMouseLeave={() => setHoverFriend(false)}
            className="w-9 h-9 rounded-lg border text-lg hover:bg-gray-100 transition"
            aria-label="Friend actions"
          >
            {status === "FRIENDS" ? "✔" : "➕"}
          </button>

          {hoverFriend && <Tooltip>Friend</Tooltip>}

          {showRemove && (
            <div className="absolute right-0 top-11 z-20 w-40 rounded-xl border border-gray-200 bg-white py-1 shadow-lg">
              <MenuItem
                danger
                onClick={() =>
                  runAsync(async () => {
                    await unfriendApi(userId);
                    setStatus(userId, "NONE");
                    setShowRemove(false);
                  })
                }
              >
                Unfriend
              </MenuItem>
            </div>
          )}
        </div>
      )}

      <div className="relative">
        <button
          onClick={() => {
            setMenuOpen((v) => !v);
            setShowRemove(false);
          }}
          onMouseEnter={() => setHoverMore(true)}
          onMouseLeave={() => setHoverMore(false)}
          className="w-9 h-9 rounded-lg border text-lg hover:bg-gray-100 transition"
          aria-label="More actions"
        >
          ⋯
        </button>

        {hoverMore && <Tooltip>More</Tooltip>}

        {menuOpen && (
          <div className="absolute right-0 top-11 z-20 w-44 rounded-xl border border-gray-200 bg-white py-1 shadow-lg">
            <MenuItem>View full profile</MenuItem>
            {!isSelf && (
              <div
                className="relative"
                onMouseEnter={() => setHoverInviteToGroup(true)}
                onMouseLeave={() => setHoverInviteToGroup(false)}
              >
                <MenuItem>Invite to group</MenuItem>
                {hoverInviteToGroup && (
                  <div className="absolute right-full top-0 mr-2 max-h-56 w-56 overflow-y-auto rounded-xl border border-gray-200 bg-white py-1 shadow-lg">
                    {inviteableGroups.length === 0 ? (
                      <div className="px-3 py-2 text-xs text-gray-500">No group available</div>
                    ) : (
                      inviteableGroups.map((room) => (
                        <button
                          key={room.id}
                          type="button"
                          onClick={() => sendGroupInvite(room.id)}
                          className="w-full truncate px-3 py-2 text-left text-sm text-gray-700 transition hover:bg-gray-50"
                        >
                          {room.name}
                        </button>
                      ))
                    )}
                  </div>
                )}
              </div>
            )}
            {!isSelf && (
              <MenuItem
                danger
                onClick={() =>
                  runAsync(async () => {
                    await blockUserApi(userId);
                    setStatus(userId, "BLOCKED_BY_ME");
                    close();
                  })
                }
              >
                Block
              </MenuItem>
            )}
          </div>
        )}
      </div>
    </>
  );

  return (
    <div
      ref={popupRef}
      style={style}
      className="animate-in fade-in zoom-in-95"
    >
      <ProfileIdentityCard
        presentation={presentation}
        className="shadow-xl overflow-visible"
        topActions={topActions}
      >
        {isSelf ? (
          <button
            onClick={openProfileSettings}
            className="w-full rounded-xl bg-indigo-600 py-2 text-sm font-medium text-white hover:bg-indigo-500 transition"
          >
            Go to Profile Settings
          </button>
        ) : (
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <input
                value={dmText}
                onChange={(e) => setDmText(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    void startMiniDM();
                  }
                }}
                placeholder="Send a message..."
                className="w-full rounded-xl border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <button
                type="button"
                aria-label="Insert emoji"
                onClick={() => setDmText((prev) => `${prev}🙂`)}
                className="h-10 w-10 rounded-xl border text-lg hover:bg-gray-100 transition"
              >
                🙂
              </button>
            </div>
            <p className="text-xs text-gray-500">Press Enter to jump into full chat.</p>
          </div>
        )}
      </ProfileIdentityCard>
    </div>
  );
}

/* ================= UI ================= */

function Tooltip({ children }: { children: React.ReactNode }) {
  return (
    <div className="absolute bottom-11 left-1/2 -translate-x-1/2">
      <div className="relative px-2 py-1 text-xs bg-gray-900 text-white rounded-md shadow whitespace-nowrap">
        {children}
        <div className="absolute left-1/2 -translate-x-1/2 top-full w-2 h-2 bg-gray-900 rotate-45" />
      </div>
    </div>
  );
}

function MenuItem({
  children,
  onClick,
  danger,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  danger?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      className={`w-full text-left px-3 py-2 text-sm transition ${danger
        ? "text-red-600 hover:bg-red-50"
        : "hover:bg-gray-50"
        }`}
    >
      {children}
    </button>
  );
}