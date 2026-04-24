import { useEffect, useState } from "react";
import { usePresenceStore } from "../../store/presence.store";
import { useUserStore } from "../../store/user.store";
import { getRoomMembers } from "../../api/room.service";
import { onChatEvent } from "../../websocket/chat.socket";
import { ChatEventType } from "../../constants/chatEvents";

import TypingDots from "../presence/TypingDots";
import UserAvatar from "../user/UserAvatar";
import Username from "../user/Username";
import type { PresenceStatus } from "../../types/presence";
import { PRESENCE_STATUS_ORDER } from "../../utils/presenceStatus";
import { isFeatureEnabled } from "../../config/featureFlags";

interface Props {
  roomId: string;
}

interface MemberRaw {
  userId: string;
  role: string;
}

export default function RoomMembersSidebar({ roomId }: Props) {
  const [membersRaw, setMembersRaw] = useState<MemberRaw[]>([]);
  const [showOffline, setShowOffline] = useState(true);
  const groupingEnabled = isFeatureEnabled("enableRoomMemberStatusGrouping");

  const userStatusesSnapshot = usePresenceStore((s) => s.userStatuses);
  const usersSnapshot = useUserStore((s) => s.users);
  const fetchUsers = useUserStore((s) => s.fetchUsers);

  const userStatuses = userStatusesSnapshot ?? {};

  const getStatus = (userId: string) => userStatuses[userId] ?? "OFFLINE";

  useEffect(() => {
    if (!roomId) return;

    getRoomMembers(roomId)
      .then((members) => {
        setMembersRaw(members);
        fetchUsers(members.map((m) => m.userId));
      })
      .catch(() => {});
  }, [roomId, fetchUsers]);

  useEffect(() => {
    if (!roomId) return;

    return onChatEvent((event) => {
      if (event.type === ChatEventType.MEMBER_JOINED) {
        const p = event.payload;
        if (p.roomId !== roomId) return;
        setMembersRaw((prev) => {
          if (prev.some((m) => m.userId === p.userId)) return prev;
          return [...prev, { userId: p.userId, role: p.role }];
        });
        fetchUsers([p.userId]);
      } else if (event.type === ChatEventType.MEMBER_LEFT) {
        const p = event.payload;
        if (p.roomId !== roomId) return;
        setMembersRaw((prev) => prev.filter((m) => m.userId !== p.userId));
      }
    });
  }, [roomId, fetchUsers]);

  const sortMembers = (members: MemberRaw[]) =>
    [...members].sort((a, b) => {
      const statusDiff = PRESENCE_STATUS_ORDER[getStatus(a.userId)] - PRESENCE_STATUS_ORDER[getStatus(b.userId)];
      if (statusDiff !== 0) return statusDiff;

      const nameA =
        usersSnapshot[a.userId]?.displayName ||
        usersSnapshot[a.userId]?.username ||
        a.userId;
      const nameB =
        usersSnapshot[b.userId]?.displayName ||
        usersSnapshot[b.userId]?.username ||
        b.userId;
      return nameA.localeCompare(nameB, undefined, { sensitivity: "base" });
    });

  const owners = sortMembers(membersRaw.filter((m) => m.role === "OWNER"));
  const onlineMembers = sortMembers(
    membersRaw.filter(
      (m) => m.role !== "OWNER" && (getStatus(m.userId) === "ONLINE" || getStatus(m.userId) === "AWAY")
    )
  );
  const offlineMembers = sortMembers(
    membersRaw.filter((m) => m.role !== "OWNER" && getStatus(m.userId) === "OFFLINE")
  );

  const renderMembers = (list: MemberRaw[]) =>
    list.map((m) => (
      <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
    ));

  return (
    <div className="h-full flex flex-col bg-white">
      <div className="px-3 py-2.5 border-b flex items-center justify-between gap-2">
        <span className="font-semibold text-sm text-gray-700">Members</span>
        <button
          type="button"
          onClick={() => setShowOffline((prev) => !prev)}
          className="rounded border border-gray-200 px-2 py-1 text-[11px] font-medium uppercase tracking-wide text-gray-600 hover:bg-gray-50"
        >
          {showOffline ? "Hide Offline" : "Show Offline"}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-2 py-3 space-y-4">
        {groupingEnabled ? (
          <>
            {owners.length > 0 && (
              <Group title="Owner" count={owners.length}>
                {renderMembers(owners)}
              </Group>
            )}

            {onlineMembers.length > 0 && (
              <Group title="Online" count={onlineMembers.length}>
                {renderMembers(onlineMembers)}
              </Group>
            )}

            {showOffline && offlineMembers.length > 0 && (
              <Group title="Offline" count={offlineMembers.length}>
                {renderMembers(offlineMembers)}
              </Group>
            )}
          </>
        ) : (
          <Group title="Members" count={membersRaw.length}>
            {renderMembers(sortMembers(membersRaw))}
          </Group>
        )}

        {membersRaw.length === 0 && (
          <div className="text-xs text-gray-400 text-center py-6">No members</div>
        )}
      </div>
    </div>
  );
}

function Group({
  title,
  count,
  children,
}: {
  title: string;
  count: number;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div className="sticky top-0 z-10 mb-1.5 px-1 py-1 text-[11px] font-semibold uppercase tracking-wider text-gray-500 bg-white/95 backdrop-blur-sm">
        {title} ({count})
      </div>
      <div className="space-y-0.5">{children}</div>
    </div>
  );
}

function MemberRow({
  userId,
  role,
  roomId,
  status,
}: {
  userId: string;
  role: string;
  roomId: string;
  status: PresenceStatus;
}) {
  const user = useUserStore((s) => s.users[userId]);
  const typing = usePresenceStore((s) => !!s.typingByRoom[roomId]?.[userId]);

  return (
    <div className="flex items-center gap-2 px-1 py-1 rounded-md hover:bg-gray-50 transition-colors">
      <div className="relative flex-shrink-0">
        <UserAvatar userId={userId} avatar={user?.avatarUrl} size={28} status={status} />
      </div>

      <Username userId={userId}>
        <span className="text-sm truncate flex items-center gap-1 text-gray-700">
          {user?.displayName || user?.username || "Loading..."}

          {role === "OWNER" && (
            <span className="text-yellow-500 text-xs leading-none">👑</span>
          )}

          {typing && <TypingDots className="ml-1 scale-75 origin-left" />}
        </span>
      </Username>
    </div>
  );
}
