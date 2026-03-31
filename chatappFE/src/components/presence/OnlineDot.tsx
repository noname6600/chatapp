import { usePresenceStore } from "../../store/presence.store";
import type { PresenceStatus } from "../../types/presence";

const statusColorMap = {
  ONLINE: "bg-green-500",
  AWAY: "bg-amber-400",
  OFFLINE: "bg-gray-400",
} as const;

export default function OnlineDot({
  userId,
  roomId,
  status: statusProp,
}: {
  userId: string;
  roomId?: string;
  status?: PresenceStatus;
}) {
  const status = usePresenceStore((s) => {
    if (statusProp) {
      return statusProp;
    }

    if (roomId) {
      return s.getRoomUserStatus?.(roomId, userId) ?? s.userStatuses?.[userId] ?? "OFFLINE";
    }

    return s.getUserStatus?.(userId) ?? s.userStatuses?.[userId] ?? "OFFLINE";
  });

  return (
    <span
      className={`w-2.5 h-2.5 rounded-full ${
        statusColorMap[status]
      }`}
    />
  );
}
