import { useRef } from "react";
import { useUserOverlay } from "../../store/userOverlay.store";
import type {OverlaySource} from "../../store/userOverlay.store";
interface Props {
  userId: string;
  avatar?: string | null;
  size?: number;
}

export default function UserAvatar({ userId, avatar, size = 32 }: Props) {
  const ref = useRef<HTMLImageElement>(null);
  const open = useUserOverlay((s) => s.open);

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
    <img
      ref={ref}
      src={avatar || "/default-avatar.png"}
      draggable={false}
      onClick={handleClick}
      style={{ width: size, height: size }}
      className="rounded-full cursor-pointer"
    />
  );
}