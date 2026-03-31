import { useRef } from "react";
import type { ReactNode } from "react";
import { useUserOverlay } from "../../store/userOverlay.store";

interface Props {
  userId: string;
  children: ReactNode;
}

export default function Username({ userId, children }: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const open = useUserOverlay((s) => s.open);

  const handleClick = () => {
    if (!ref.current) return;
    open(userId, ref.current.getBoundingClientRect(), "CHAT");
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