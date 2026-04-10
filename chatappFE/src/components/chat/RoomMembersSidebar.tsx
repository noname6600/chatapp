import { useEffect, useState } from "react";
import { usePresenceStore } from "../../store/presence.store";
import { useUserStore } from "../../store/user.store";
import { getRoomMembers } from "../../api/room.service";

import OnlineDot from "../presence/OnlineDot";
import UserAvatar from "../user/UserAvatar";
import Username from "../user/Username";

interface Props {
  roomId: string;
}

interface MemberRaw {
  userId: string;
  role: string;
}

export default function RoomMembersSidebar({ roomId }: Props) {
  const [membersRaw, setMembersRaw] = useState<MemberRaw[]>([]);

  const userStatusesSnapshot = usePresenceStore((s) => s.userStatuses);
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

  const owners = membersRaw.filter((m) => m.role === "OWNER");
  const onlineMembers = membersRaw.filter(
    (m) => m.role !== "OWNER" && getStatus(m.userId) === "ONLINE"
  );
  const awayMembers = membersRaw.filter(
    (m) => m.role !== "OWNER" && getStatus(m.userId) === "AWAY"
  );
  const offlineMembers = membersRaw.filter(
    (m) => m.role !== "OWNER" && getStatus(m.userId) === "OFFLINE"
  );

  return (
    <div className="h-full flex flex-col bg-white">
      <div className="px-3 py-2.5 border-b font-semibold text-sm text-gray-700">Members</div>

      <div className="flex-1 overflow-y-auto px-2 py-3 space-y-4">
        {owners.length > 0 && (
          <Group title="Owner">
            {owners.map((m) => (
              <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
            ))}
          </Group>
        )}

        {onlineMembers.length > 0 && (
          <Group title="Online">
            {onlineMembers.map((m) => (
              <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
            ))}
          </Group>
        )}

        {awayMembers.length > 0 && (
          <Group title="Away">
            {awayMembers.map((m) => (
              <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
            ))}
          </Group>
        )}

        {offlineMembers.length > 0 && (
          <Group title="Offline">
            {offlineMembers.map((m) => (
              <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
            ))}
          </Group>
        )}

        {membersRaw.length === 0 && (
          <div className="text-xs text-gray-400 text-center py-6">No members</div>
        )}
      </div>

      <style>
        {`
        .typing-dots::after {
          content: '';
          animation: dots 1.5s steps(3, end) infinite;
        }
        @keyframes dots {
          0% { content: ''; }
          33% { content: '.'; }
          66% { content: '..'; }
          100% { content: '...'; }
        }
      `}
      </style>
    </div>
  );
}

function Group({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div className="text-[11px] font-semibold uppercase tracking-wider text-gray-400 mb-1.5 px-1">{title}</div>
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
  status: "ONLINE" | "AWAY" | "OFFLINE";
}) {
  const user = useUserStore((s) => s.users[userId]);
  const typing = usePresenceStore((s) => !!s.typingByRoom[roomId]?.[userId]);

  return (
    <div className="flex items-center gap-2 px-1 py-1 rounded-md hover:bg-gray-50 transition-colors">
      <div className="relative flex-shrink-0">
        <UserAvatar userId={userId} avatar={user?.avatarUrl} size={28} />
        <div className="absolute -bottom-0.5 -right-0.5">
          <OnlineDot userId={userId} status={status} />
        </div>
      </div>

      <Username userId={userId}>
        <span className="text-sm truncate flex items-center gap-1 text-gray-700">
          {user?.displayName || user?.username || "Loading..."}

          {role === "OWNER" && (
            <span className="text-yellow-500 text-xs leading-none">👑</span>
          )}

          {typing && (
            <span className="typing-dots text-xs text-gray-400" />
          )}
        </span>
      </Username>
    </div>
  );
}