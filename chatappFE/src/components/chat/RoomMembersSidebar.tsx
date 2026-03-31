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
  const [open, setOpen] = useState(true);

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
    <>
      <button
        onClick={() => setOpen((v) => !v)}
        className="absolute right-2 top-2 z-10 text-xs bg-gray-200 hover:bg-gray-300 px-2 py-1 rounded"
      >
        {open ? "Hide" : "Show"} Users
      </button>

      {open && (
        <div className="w-56 border-l bg-white flex flex-col">
          <div className="p-3 border-b font-semibold text-sm">Members</div>

          <div className="flex-1 overflow-y-auto p-3 space-y-4">
            {owners.length > 0 && (
              <Group title="OWNER">
                {owners.map((m) => (
                  <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
                ))}
              </Group>
            )}

            {onlineMembers.length > 0 && (
              <Group title="ONLINE">
                {onlineMembers.map((m) => (
                  <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
                ))}
              </Group>
            )}

            {awayMembers.length > 0 && (
              <Group title="AWAY">
                {awayMembers.map((m) => (
                  <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
                ))}
              </Group>
            )}

            {offlineMembers.length > 0 && (
              <Group title="OFFLINE">
                {offlineMembers.map((m) => (
                  <MemberRow key={m.userId} {...m} roomId={roomId} status={getStatus(m.userId)} />
                ))}
              </Group>
            )}
          </div>
        </div>
      )}

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
    </>
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
      <div className="text-xs font-semibold text-gray-500 mb-2">{title}</div>
      <div className="space-y-2">{children}</div>
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
    <div className="flex items-center gap-2 text-sm">
      {/* 🟢 AVATAR */}
      <UserAvatar userId={userId} avatar={user?.avatarUrl} size={24} />

      {/* 🟢 USERNAME */}
      <Username userId={userId}>
        <span className="truncate flex items-center gap-1">
          {user?.displayName || user?.username || "Loading..."}

          {role === "OWNER" && (
            <span className="text-yellow-500 text-xs">👑</span>
          )}

          {typing && (
            <span className="typing-dots text-xs text-gray-500">...</span>
          )}
        </span>
      </Username>

      <div className="ml-auto flex items-center gap-2">
        <span className="text-[11px] uppercase tracking-wide text-gray-400">
          {status}
        </span>
        <OnlineDot userId={userId} status={status} />
      </div>
    </div>
  );
}