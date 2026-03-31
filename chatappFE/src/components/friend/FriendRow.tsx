import { useUserStore } from "../../store/user.store";
import { usePresenceStore } from "../../store/presence.store";
import OnlineDot from "../presence/OnlineDot";
import { MoreMenu } from "./MoreMenu";

interface Props {
  userId: string;
  variant: "pending" | "friend";
  onRemove?: (id: string) => void;
  onAccept?: (id: string) => void;
  onDecline?: (id: string) => void;
  onCancel?: (id: string) => void;
  onChat?: (id: string) => void;
}

export function FriendRow({
  userId,
  variant,
  onRemove,
  onAccept,
  onDecline,
  onCancel,
  onChat,
}: Props) {
  const user = useUserStore((s) => s.users[userId]);
  const status = usePresenceStore((s) => s.getUserStatus(userId));

  if (!user) return null;

  return (
    <div
      className="
        flex items-center
        w-full
        p-3
        rounded
        bg-gray-100 hover:bg-gray-200
        transition
        gap-3
      "
    >
      {/* Avatar with status ring */}
      <div className="relative w-10 h-10 rounded-full bg-gray-300 flex-shrink-0 overflow-hidden">
        {user.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt={user.displayName}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-sm">
            👤
          </div>
        )}
        {variant === "friend" && (
          <div className="absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-white flex items-center justify-center">
            <OnlineDot userId={userId} />
          </div>
        )}
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="font-medium truncate">{user.displayName}</div>

        {variant === "pending" ? (
          <div className="text-sm text-gray-500 truncate">
            @{user.username}
          </div>
        ) : (
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <OnlineDot userId={userId} />
            {status === "AWAY" ? "Away" : status === "ONLINE" ? "Online" : "Offline"}
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2">
        {variant === "pending" && (
          <>
            {onAccept && (
              <IconButton title="Accept" onClick={() => onAccept(userId)}>
                ✅
              </IconButton>
            )}

            {onDecline && (
              <IconButton title="Decline" onClick={() => onDecline(userId)}>
                ❌
              </IconButton>
            )}

            {onCancel && (
              <button
                onClick={() => onCancel(userId)}
                className="px-3 py-1 text-sm rounded hover:bg-gray-300"
              >
                Cancel
              </button>
            )}
          </>
        )}

        {variant === "friend" && (
          <>
            {/* Chat trước */}
            <IconButton title="Chat" onClick={() => onChat?.(userId)}>
              💬
            </IconButton>

            {/* More sau */}
            {onRemove && <MoreMenu onRemove={() => onRemove(userId)} />}
          </>
        )}
      </div>
    </div>
  );
}

function IconButton({
  children,
  onClick,
  title,
}: {
  children: React.ReactNode;
  onClick: () => void;
  title?: string;
}) {
  return (
    <button
      title={title}
      onClick={onClick}
      className="
        w-9 h-9
        flex items-center justify-center
        rounded
        hover:bg-gray-300
        transition
        text-lg
      "
    >
      {children}
    </button>
  );
}