import { resolveProfilePresentation } from "../../utils/profilePresentation";
import ProfileIdentityCard from "./ProfileIdentityCard";

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
  const presentation = resolveProfilePresentation(draft);

  return (
    <div className="flex justify-center">
      <ProfileIdentityCard
        presentation={presentation}
        className="w-72"
        headerClassName="h-16"
        avatarClassName="h-16 w-16"
        avatarSize={64}
        compact
        bodyClassName="text-center"
      >
        <button className="w-full rounded-xl bg-indigo-600 py-2 text-xs font-medium text-white transition hover:bg-indigo-500">
          Edit Profile
        </button>
      </ProfileIdentityCard>
    </div>
  );
}