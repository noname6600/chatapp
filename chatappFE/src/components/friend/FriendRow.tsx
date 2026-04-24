import { useUserStore } from "../../store/user.store";
import { usePresenceStore } from "../../store/presence.store";
import { MoreMenu } from "./MoreMenu";
import UserAvatar from "../user/UserAvatar";

interface Props {
  userId: string;
  variant: "pending" | "friend";
  onRemove?: (id: string) => void;
  onAccept?: (id: string) => void;
  onDecline?: (id: string) => void;
  onCancel?: (id: string) => void;
  onChat?: (id: string) => void;
  isChatLaunching?: boolean;
}

export function FriendRow({
  userId,
  variant,
  onRemove,
  onAccept,
  onDecline,
  onCancel,
  onChat,
  isChatLaunching = false,
}: Props) {
  const user = useUserStore((s) => s.users[userId]);
  const status = usePresenceStore((s) => s.getUserStatus(userId));
  const isFriendVariant = variant === "friend";
  const canOpenChat = isFriendVariant && Boolean(onChat) && !isChatLaunching;

  const handleOpenChat = () => {
    if (!canOpenChat) return;
    onChat?.(userId);
  };

  if (!user) return null;

  return (
    <div
      role={isFriendVariant ? "button" : undefined}
      tabIndex={isFriendVariant ? 0 : undefined}
      onClick={handleOpenChat}
      onKeyDown={(event) => {
        if (!canOpenChat) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onChat?.(userId);
        }
      }}
      className="
        flex items-center justify-between
        w-full
        p-3.5
        rounded-xl border border-gray-200
        bg-white
        transition-all
        gap-3
        hover:border-gray-300 hover:shadow-sm
        focus:outline-none focus:ring-2 focus:ring-blue-500/40
        cursor-default
      "
      data-testid={`friend-row-${variant}-${userId}`}
    >
      {/* Avatar with status dot */}
      <div className="relative w-11 h-11 flex-shrink-0">
        <UserAvatar userId={userId} avatar={user.avatarUrl} size={44} status={status} />
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="font-medium text-gray-900 truncate">{user.displayName}</div>

        {variant === "pending" ? (
          <div className="text-sm text-gray-500 truncate">
            @{user.username}
          </div>
        ) : (
          <div className="text-sm text-gray-500">
            {status === "AWAY" ? "Away" : status === "ONLINE" ? "Online" : "Offline"}
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2" onClick={(event) => event.stopPropagation()}>
        {variant === "pending" && (
          <>
            {onAccept && (
              <IconButton title="Accept" ariaLabel="Accept request" onClick={() => onAccept(userId)}>
                ✅
              </IconButton>
            )}

            {onDecline && (
              <IconButton title="Decline" ariaLabel="Decline request" onClick={() => onDecline(userId)}>
                ❌
              </IconButton>
            )}

            {onCancel && (
              <button
                onClick={(event) => {
                  event.stopPropagation();
                  onCancel(userId);
                }}
                className="px-3 py-1 text-sm rounded-lg border border-gray-200 hover:bg-gray-100"
                aria-label="Cancel request"
              >
                Cancel
              </button>
            )}
          </>
        )}

        {variant === "friend" && (
          <>
            <IconButton
              title="Chat"
              ariaLabel="Open chat"
              disabled={isChatLaunching}
              onClick={() => onChat?.(userId)}
            >
              {isChatLaunching ? "…" : "💬"}
            </IconButton>

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
  ariaLabel,
  disabled = false,
}: {
  children: React.ReactNode;
  onClick: () => void;
  title?: string;
  ariaLabel?: string;
  disabled?: boolean;
}) {
  return (
    <button
      title={title}
      aria-label={ariaLabel}
      disabled={disabled}
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
      className="
        w-9 h-9
        flex items-center justify-center
        rounded-lg border border-gray-200
        hover:bg-gray-100
        disabled:cursor-not-allowed disabled:opacity-60
        transition
        text-lg
      "
    >
      {children}
    </button>
  );
}
