import UserAvatar from "../user/UserAvatar";
import { useUserStore } from "../../store/user.store";
import TypingDots from "./TypingDots";
import clsx from "clsx";

interface TypingUsersProps {
  userIds: string[];
  className?: string;
}

export default function TypingUsers({
  userIds,
  className,
}: TypingUsersProps) {
  const users = useUserStore((s) => s.users);

  if (userIds.length === 0) return null;

  const typingNames = userIds
    .map(
      (id) =>
        users[id]?.displayName || users[id]?.username || "Someone"
    )
    .slice(0, 3);

  const typingText =
    userIds.length > 3
      ? "Multiple people are typing"
      : userIds.length === 1
        ? `${typingNames[0]} is typing`
        : `${typingNames.join(", ")} are typing`;

  return (
    <div className={clsx("flex items-center gap-3", className)}>
      <div className="flex -space-x-2">
        {userIds.slice(0, 3).map((id) => (
          <div key={id} className="ring-2 ring-white">
            <UserAvatar userId={id} avatar={users[id]?.avatarUrl} size={24} />
          </div>
        ))}
      </div>

      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600">{typingText}</span>
        <TypingDots />
      </div>
    </div>
  );
}
