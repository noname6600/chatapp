import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Check, User, UserPlus } from "lucide-react";
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
import EmojiPicker from "../chat/EmojiPicker";

type FriendshipStatus =
  | "NONE"
  | "FRIENDS"
  | "REQUEST_SENT"
  | "REQUEST_RECEIVED"
  | "BLOCKED_BY_ME"
  | "BLOCKED_ME";

type FriendActionState = {
  label: string;
  disabled: boolean;
  style: "primary" | "pending" | "neutral";
  icon: React.ReactNode;
  ariaLabel: string;
};

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
  const dmInputRef = useRef<HTMLInputElement>(null);

  const isSelf = userId === myId;
  const presentation = resolveProfilePresentation(user ?? {}, { fallbackAbout: false });
  const inviteableGroups = Object.values(roomsById)
    .filter((room) => room.type === "GROUP")
    .sort((a, b) => a.name.localeCompare(b.name));

  useEffect(() => {
    setMenuOpen(false);
    setHoverInviteToGroup(false);
    setShowRemove(false);
    setDmText("");
  }, [userId]);

  useEffect(() => {
    if (!userId) return;

    if (!user) fetchUsers([userId]);
    if (!status && !isSelf) resolve(userId);
  }, [userId, user, status, fetchUsers, resolve, isSelf]);

  useEffect(() => {
    if (!userId) return;

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

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  if (!userId || !rect) return null;

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

  const showPresence = source !== "FRIEND_SEARCH";

  const friendAction: FriendActionState = (() => {
    if (isSelf) {
      return {
        label: "Self",
        disabled: true,
        style: "neutral",
        icon: <User size={16} />,
        ariaLabel: "Your profile",
      };
    }

    switch (status) {
      case "NONE":
        return {
          label: "Add Friend",
          disabled: loading,
          style: "primary",
          icon: <UserPlus size={16} />,
          ariaLabel: "Add Friend",
        };
      case "REQUEST_SENT":
        return {
          label: "Pending",
          disabled: true,
          style: "pending",
          icon: <User size={16} />,
          ariaLabel: "Pending friend request",
        };
      case "FRIENDS":
        return {
          label: "Friends",
          disabled: loading,
          style: "neutral",
          icon: <Check size={16} />,
          ariaLabel: "Friends",
        };
      case "REQUEST_RECEIVED":
        return {
          label: "Request Received",
          disabled: true,
          style: "neutral",
          icon: <User size={16} />,
          ariaLabel: "Friend request received",
        };
      case "BLOCKED_BY_ME":
      case "BLOCKED_ME":
        return {
          label: "Unavailable",
          disabled: true,
          style: "neutral",
          icon: <User size={16} />,
          ariaLabel: "Friend request unavailable",
        };
      default:
        return {
          label: "Loading...",
          disabled: true,
          style: "neutral",
          icon: <User size={16} />,
          ariaLabel: "Loading friend status",
        };
    }
  })();

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
        try {
          await sendFriendRequestApi(userId);
          setStatus(userId, "REQUEST_SENT");
        } catch (error) {
          console.error("Failed to send friend request", error);
        }
      });
    }

    if (status === "FRIENDS") {
      setShowRemove((v) => !v);
      setMenuOpen(false);
    }
  };

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

  const insertPopupEmoji = (emoji: string) => {
    const input = dmInputRef.current;
    const start = input?.selectionStart ?? dmText.length;
    const end = input?.selectionEnd ?? dmText.length;
    const nextText = `${dmText.slice(0, start)}${emoji}${dmText.slice(end)}`;
    setDmText(nextText);

    requestAnimationFrame(() => {
      dmInputRef.current?.focus();
      const nextCursor = start + emoji.length;
      dmInputRef.current?.setSelectionRange(nextCursor, nextCursor);
    });
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

  const friendActionClass =
    friendAction.style === "primary"
      ? "border-indigo-500 bg-indigo-500 text-white shadow-sm hover:bg-indigo-600"
      : friendAction.style === "pending"
        ? "border-amber-300 bg-amber-50 text-amber-700"
        : "border-gray-300 bg-white text-gray-700 hover:bg-gray-50";

  const topActions = (
    <div className="flex max-w-[190px] flex-wrap items-center justify-end gap-2">
      {!isSelf && (
        <div className="relative">
          <button
            disabled={friendAction.disabled}
            onClick={handleFriendClick}
            onMouseEnter={() => setHoverFriend(true)}
            onMouseLeave={() => setHoverFriend(false)}
            className={`inline-flex h-9 max-w-[150px] items-center gap-1.5 rounded-lg border px-2.5 text-xs font-semibold transition ${friendActionClass} ${friendAction.disabled ? "cursor-not-allowed" : ""}`}
            aria-label={friendAction.ariaLabel}
          >
            {friendAction.icon}
            <span className="truncate">{friendAction.label}</span>
          </button>

          {hoverFriend && <Tooltip>{friendAction.label}</Tooltip>}

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
          className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-gray-300 bg-white text-lg text-gray-700 transition hover:bg-gray-50"
          aria-label="More actions"
        >
          ...
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
    </div>
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
        userId={userId}
        avatarSize={80}
        showPresence={showPresence}
      >
        {isSelf ? (
          <button
            onClick={openProfileSettings}
            className="w-full rounded-xl bg-indigo-600 py-2 text-sm font-medium text-white hover:bg-indigo-500 transition"
          >
            Go to Profile Settings
          </button>
        ) : (
          <div className="space-y-2.5">
            <div className="flex flex-wrap items-end gap-2">
              <input
                ref={dmInputRef}
                value={dmText}
                onChange={(e) => setDmText(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    void startMiniDM();
                  }
                }}
                placeholder="Send a message..."
                className="min-w-0 flex-1 rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <EmojiPicker
                onEmojiSelect={insertPopupEmoji}
                disabled={loading}
                className="self-center"
                triggerClassName="h-10 w-10 rounded-xl border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 hover:text-gray-800"
              />
            </div>
            <p className="text-xs text-gray-500">Press Enter to jump into full chat.</p>
          </div>
        )}
      </ProfileIdentityCard>
    </div>
  );
}

function Tooltip({ children }: { children: React.ReactNode }) {
  return (
    <div className="absolute bottom-11 left-1/2 -translate-x-1/2">
      <div className="relative whitespace-nowrap rounded-md bg-gray-900 px-2 py-1 text-xs text-white shadow">
        {children}
        <div className="absolute left-1/2 top-full h-2 w-2 -translate-x-1/2 rotate-45 bg-gray-900" />
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
      className={`w-full px-3 py-2 text-left text-sm transition ${
        danger
          ? "text-red-600 hover:bg-red-50"
          : "hover:bg-gray-50"
      }`}
    >
      {children}
    </button>
  );
}
