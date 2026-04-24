import { useRef } from "react";
import { useUserOverlay } from "../../store/userOverlay.store";
import type {OverlaySource} from "../../store/userOverlay.store";
import { usePresenceStore } from "../../store/presence.store";
import type { PresenceStatus } from "../../types/presence";
import UserStatusFrame from "../presence/UserStatusFrame";
import AvatarImage from "./AvatarImage";
interface Props {
  userId: string;
  avatar?: string | null;
  size?: number;
  status?: PresenceStatus;
}

export default function UserAvatar({ userId, avatar, size = 32, status }: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const open = useUserOverlay((s) => s.open);
  const resolvedStatus = usePresenceStore((s) => status ?? s.getUserStatus(userId));

  const handleClick = () => {
    if (!ref.current) return;

    const rect = ref.current.getBoundingClientRect();

    // ✅ Detect vị trí avatar trên màn hình
    const isRightSide = rect.left > window.innerWidth * 0.55;

    const source: OverlaySource = isRightSide
      ? "SIDEBAR"
      : "CHAT";

    open(userId, rect, source);
  };

  return (
    <UserStatusFrame status={resolvedStatus} size={size}>
      <AvatarImage
        ref={ref}
        src={avatar || "/default-avatar.png"}
        alt={`${userId} avatar`}
        size={size}
        onClick={handleClick}
        draggable={false}
      />
    </UserStatusFrame>
  );
}