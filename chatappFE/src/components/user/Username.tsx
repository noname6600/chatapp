import { useRef } from "react";
import type { ReactNode } from "react";
import { useUserOverlay } from "../../store/userOverlay.store";
import type { OverlaySource } from "../../store/userOverlay.store";

interface Props {
  userId: string;
  children: ReactNode;
  source?: OverlaySource;
}

export default function Username({ userId, children, source = "CHAT" }: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const open = useUserOverlay((s) => s.open);

  const handleClick = () => {
    if (!ref.current) return;
    open(userId, ref.current.getBoundingClientRect(), source);
  };

  return (
    <span
      ref={ref}
      className="cursor-pointer hover:underline"
      onClick={handleClick}
    >
      {children}
    </span>
  );
}