import type { ProfilePresentation } from "../../utils/profilePresentation";
import { usePresenceStore } from "../../store/presence.store";
import type { PresenceStatus } from "../../types/presence";
import UserStatusFrame from "../presence/UserStatusFrame";
import AvatarImage from "../user/AvatarImage";

interface Props {
  presentation: ProfilePresentation;
  className?: string;
  bodyClassName?: string;
  headerClassName?: string;
  avatarClassName?: string;
  compact?: boolean;
  topActions?: React.ReactNode;
  children?: React.ReactNode;
  aboutTestId?: string;
  userId?: string;
  status?: PresenceStatus;
  avatarSize?: number;
  showPresence?: boolean;
}

export default function ProfileIdentityCard({
  presentation,
  className = "",
  bodyClassName = "",
  headerClassName = "h-20",
  avatarClassName = "w-20 h-20",
  compact = false,
  topActions,
  children,
  aboutTestId = "profile-about-text",
  userId,
  status,
  avatarSize = 80,
  showPresence = true,
}: Props) {
  const resolvedStatus = usePresenceStore((s) => {
    if (status) return status;
    if (!userId) return "OFFLINE";
    return s.getUserStatus(userId);
  });

  return (
    <div className={`overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm ${className}`.trim()}>
      <div
        data-testid="profile-header-bg"
        className={headerClassName}
        style={{ background: presentation.backgroundColor }}
      />

      <div className={`px-4 pb-4 ${bodyClassName}`.trim()}>
        <div className={`${compact ? "-mt-8" : "-mt-10"} flex items-start justify-between gap-3`}>
          {showPresence ? (
            <UserStatusFrame status={resolvedStatus} size={avatarSize} borderWidth={6}>
              <AvatarImage
                src={presentation.avatarUrl}
                alt={`${presentation.displayName} avatar`}
                size={avatarSize}
                className={`${avatarClassName} border-4 border-white shadow`.trim()}
                draggable={false}
              />
            </UserStatusFrame>
          ) : (
            <AvatarImage
              src={presentation.avatarUrl}
              alt={`${presentation.displayName} avatar`}
              size={avatarSize}
              className={`${avatarClassName} border-4 border-white shadow`.trim()}
              draggable={false}
            />
          )}
          {topActions ? <div className="mt-8 flex items-center gap-2">{topActions}</div> : null}
        </div>

        <div className={compact ? "mt-2" : "mt-3"}>
          <div className="text-lg font-semibold text-gray-900">{presentation.displayName}</div>
          <div className="text-sm text-gray-500">@{presentation.username}</div>
          {presentation.aboutMe ? (
            <div data-testid={aboutTestId} className="mt-2 text-sm text-gray-600">
              {presentation.aboutMe}
            </div>
          ) : null}
        </div>

        {children ? <div className="mt-4">{children}</div> : null}
      </div>
    </div>
  );
}
