import { useState } from "react";
import { useAuth } from "../store/auth.store";
import ProfileEditor from "../components/profile/ProfileEditor";
import ProfilePreview from "../components/profile/ProfilePreview";
import type { ProfileDraft } from "../types/profile";

export default function ProfileSettingsPage() {
  const { currentUser } = useAuth();

  const [draft, setDraft] = useState<ProfileDraft>({
    displayName: currentUser?.displayName ?? "",
    username: currentUser?.username ?? "",
    avatarUrl: currentUser?.avatarUrl ?? null,
    aboutMe: currentUser?.aboutMe ?? "",
    backgroundColor: currentUser?.backgroundColor ?? "#6366f1",
  });

  if (!currentUser) {
    return <div className="p-6">Loading profile...</div>;
  }

  return (
    <div className="h-full w-full">
      <div className="max-w-6xl mx-auto h-full grid grid-cols-1 lg:grid-cols-2 bg-white rounded-2xl shadow-sm overflow-hidden">
        
        {/* LEFT – EDITOR */}
        <div className="p-10 border-b lg:border-b-0 lg:border-r">
          <h1 className="text-2xl font-semibold mb-8">
            Profile Settings
          </h1>

          <div className="bg-gray-50 rounded-2xl p-6 border border-gray-200">
            <ProfileEditor draft={draft} setDraft={setDraft} />
          </div>
        </div>

        {/* RIGHT – PREVIEW */}
        <div className="p-10 bg-gray-100">
          <h2 className="text-lg font-semibold mb-6">Preview</h2>
          <ProfilePreview draft={draft} />
        </div>
      </div>
    </div>
  );
}