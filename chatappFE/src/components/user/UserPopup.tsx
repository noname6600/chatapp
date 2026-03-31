import { useEffect, useRef, useState } from "react";
import { useUserOverlay } from "../../store/userOverlay.store";
import { useUserStore } from "../../store/user.store";
import { useFriendStore } from "../../store/friend.store";
import { useAuth } from "../../store/auth.store";
import { useChat } from "../../store/chat.store";

import {
  sendFriendRequestApi,
  unfriendApi,
  blockUserApi,
} from "../../api/friend.service";

import { startPrivateChatApi } from "../../api/room.service";

type FriendshipStatus =
  | "NONE"
  | "FRIENDS"
  | "REQUEST_SENT"
  | "REQUEST_RECEIVED"
  | "BLOCKED_BY_ME"
  | "BLOCKED_ME";

export default function UserPopup() {
  const { userId, rect, source, close } = useUserOverlay();
  const { userId: myId } = useAuth();
  const { setActiveRoom, sendMessage } = useChat();

  const user = useUserStore((s) =>
    userId ? s.users[userId] : undefined
  );
  const fetchUsers = useUserStore((s) => s.fetchUsers);

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
  const [showRemove, setShowRemove] = useState(false);
  const [dmText, setDmText] = useState("");

  const isSelf = userId === myId;
  const isPending = status === "REQUEST_SENT";

  /* ================= RESET WHEN SWITCH USER ================= */
  useEffect(() => {
    setMenuOpen(false);
    setShowRemove(false);
    setDmText("");
  }, [userId]);

  /* ================= LOAD USER + FRIEND STATUS ================= */
  useEffect(() => {
    if (!userId) return;

    if (!user) fetchUsers([userId]);
    if (!status && !isSelf) resolve(userId);
  }, [userId, user, status, fetchUsers, resolve, isSelf]);

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

      setDmText("");
      close();
    });
  };

  return (
    <div
      ref={popupRef}
      style={style}
      className="
        bg-white rounded-2xl shadow-xl border border-gray-200
        animate-in fade-in zoom-in-95
      "
    >
      {/* HEADER */}
      <div
        className="h-20 rounded-t-2xl"
        style={{
          background:
            user?.backgroundColor ||
            "linear-gradient(to right, #6366f1, #a855f7)",
        }}
      />

      <div className="px-4 pb-4">
        {/* AVATAR + ACTIONS */}
        <div className="-mt-10 flex items-end gap-3">
          <img
            src={user?.avatarUrl || "/default-avatar.png"}
            className="w-20 h-20 rounded-full border-4 border-white object-cover shadow"
            draggable={false}
          />

          <div className="ml-auto flex items-center gap-2">
            {/* FRIEND BUTTON */}
            {!isSelf && (
              <div className="relative">
                <button
                  disabled={loading || isPending}
                  onClick={handleFriendClick}
                  onMouseEnter={() => setHoverFriend(true)}
                  onMouseLeave={() => setHoverFriend(false)}
                  className="
                    w-9 h-9 rounded-lg border text-lg
                    hover:bg-gray-100 transition
                  "
                >
                  {status === "FRIENDS" ? "✔" : "➕"}
                </button>

                {hoverFriend && <Tooltip>Friend</Tooltip>}

                {showRemove && (
                  <div className="absolute left-11 top-0 w-36 bg-white border rounded-xl shadow-lg py-1">
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

            {/* MORE BUTTON */}
            <div className="relative">
              <button
                onClick={() => {
                  setMenuOpen((v) => !v);
                  setShowRemove(false);
                }}
                onMouseEnter={() => setHoverMore(true)}
                onMouseLeave={() => setHoverMore(false)}
                className="
                  w-9 h-9 rounded-lg border text-lg
                  hover:bg-gray-100 transition
                "
              >
                ⋯
              </button>

              {hoverMore && <Tooltip>More</Tooltip>}

              {menuOpen && (
                <div className="absolute left-11 top-0 w-44 bg-white border rounded-xl shadow-lg py-1">
                  <MenuItem>View full profile</MenuItem>
                  {!isSelf && <MenuItem>Invite to server</MenuItem>}
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
          </div>
        </div>

        {/* USER INFO */}
        <div className="mt-3">
          <div className="text-lg font-semibold">
            {user?.displayName || "Loading..."}
          </div>
          <div className="text-sm text-gray-500">
            @{user?.username || "..."}
          </div>

          {user?.aboutMe && (
            <div className="mt-2 text-sm text-gray-600">
              {user.aboutMe}
            </div>
          )}
        </div>

        {/* MINI DM INPUT */}
        <div className="mt-4">
          {isSelf ? (
            <button className="w-full py-2 rounded-xl bg-indigo-600 text-white text-sm hover:bg-indigo-500 transition">
              Edit Profile
            </button>
          ) : (
            <input
              value={dmText}
              onChange={(e) => setDmText(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && startMiniDM()}
              placeholder="Send a message..."
              className="w-full px-3 py-2 text-sm border rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          )}
        </div>
      </div>
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