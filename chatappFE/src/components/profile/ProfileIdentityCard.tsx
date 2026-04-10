import type { ProfilePresentation } from "../../utils/profilePresentation";

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
}: Props) {
  return (
    <div className={`overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm ${className}`.trim()}>
      <div
        data-testid="profile-header-bg"
        className={headerClassName}
        style={{ background: presentation.backgroundColor }}
      />

      <div className={`px-4 pb-4 ${bodyClassName}`.trim()}>
        <div className={`${compact ? "-mt-8" : "-mt-10"} flex items-start justify-between gap-3`}>
          <img
            src={presentation.avatarUrl}
            alt={`${presentation.displayName} avatar`}
            className={`${avatarClassName} rounded-full border-4 border-white object-cover shadow`.trim()}
            draggable={false}
          />
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