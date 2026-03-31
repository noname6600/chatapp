interface Props {
  draft: {
    displayName?: string;
    username?: string;
    avatarUrl?: string | null;
    backgroundColor?: string;
    aboutMe?: string;
  };
}

export default function ProfilePreview({ draft }: Props) {
  const hasAbout = draft.aboutMe?.trim();

  return (
    <div className="flex justify-center">
      <div className="w-64 bg-white rounded-2xl shadow-md border border-gray-200 overflow-hidden">

        {/* HEADER */}
        <div
          className="h-14"
          style={{ background: draft.backgroundColor || "#6366f1" }}
        />

        <div className="px-4 pb-4">

          {/* AVATAR */}
          <div className="-mt-7 flex justify-center">
            <img
              src={draft.avatarUrl || "/default-avatar.png"}
              className="
                w-14 h-14
                rounded-full
                border-3 border-white
                object-cover
                shadow
                bg-white
              "
            />
          </div>

          {/* NAME */}
          <div className="mt-2 text-center">
            <div className="text-sm font-semibold text-gray-900">
              {draft.displayName || "Display Name"}
            </div>

            {/* USERNAME */}
            <div className="text-xs text-gray-500 mt-0.5">
              @{draft.username || "username"}
            </div>
          </div>

          {/* ABOUT */}
          {hasAbout && (
            <div className="mt-3 text-xs text-gray-600 text-center leading-relaxed">
              {draft.aboutMe}
            </div>
          )}

          {/* BUTTON */}
          <button className="w-full mt-4 py-2 rounded-xl bg-indigo-600 text-white text-xs">
            Edit Profile
          </button>
        </div>
      </div>
    </div>
  );
}