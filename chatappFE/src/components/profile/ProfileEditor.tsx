import { useEffect, useMemo, useRef, useState } from "react";
import { updateMyProfileApi, uploadAvatarApi } from "../../api/user.service";
import { useUserStore } from "../../store/user.store";
import { useAuth } from "../../store/auth.store";
import type { ProfileDraft } from "../../types/profile";

interface Props {
  draft: ProfileDraft;
  setDraft: React.Dispatch<React.SetStateAction<ProfileDraft>>;
}

export default function ProfileEditor({ draft, setDraft }: Props) {
  const updateLocal = useUserStore((s) => s.updateUserLocal);
  const { currentUser } = useAuth();

  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [status, setStatus] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  /** Snapshot ban đầu để check dirty */
  const initialRef = useRef<ProfileDraft | null>(null);

  useEffect(() => {
    if (!initialRef.current && currentUser) {
      initialRef.current = {
        username: currentUser.username,
        displayName: currentUser.displayName,
        aboutMe: currentUser.aboutMe,
        backgroundColor: currentUser.backgroundColor,
        avatarUrl: currentUser.avatarUrl,
      };
    }
  }, [currentUser]);

  const update = (key: keyof ProfileDraft, value: string) => {
    setDraft((d) => ({ ...d, [key]: value }));
    setStatus(null); // reset message khi user edit
  };

  /** Dirty check */
  const isDirty = useMemo(() => {
    if (!initialRef.current) return false;

    const initial = initialRef.current;

    return (
      draft.displayName !== initial.displayName ||
      draft.aboutMe !== initial.aboutMe ||
      draft.backgroundColor !== initial.backgroundColor ||
      draft.avatarUrl !== initial.avatarUrl
    );
  }, [draft]);

  const handleAvatarChange = async (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      setUploading(true);
      setStatus(null);

      const avatarUrl = await uploadAvatarApi(file);

      setDraft((d) => ({ ...d, avatarUrl }));
      updateLocal({ ...draft, avatarUrl } as any);

      setStatus({
        type: "success",
        message: "Avatar updated",
      });
    } catch (err) {
      setStatus({
        type: "error",
        message: (err as Error).message,
      });
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    if (saving || !isDirty) return;

    try {
      setSaving(true);
      setStatus(null);

      await updateMyProfileApi({
        displayName: draft.displayName,
        aboutMe: draft.aboutMe,
        backgroundColor: draft.backgroundColor,
      });

      updateLocal(draft as any);

      /** Update snapshot mới */
      initialRef.current = { ...draft };

      setStatus({
        type: "success",
        message: "Profile updated successfully",
      });
    } catch (err) {
      setStatus({
        type: "error",
        message: (err as Error).message,
      });
    } finally {
      setSaving(false);
    }
  };

  const inputStyle =
    "w-full px-3 py-2 text-sm rounded-xl bg-white border border-gray-300 " +
    "focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 " +
    "hover:border-gray-400 transition shadow-sm";

  return (
    <div className="space-y-5">

      {/* DISPLAY NAME */}
      <Field label="Display Name">
        <input
          value={draft.displayName}
          onChange={(e) => update("displayName", e.target.value)}
          className={inputStyle}
          placeholder="Your display name"
        />
      </Field>

      {/* ABOUT ME */}
      <Field label="About Me">
        <textarea
          value={draft.aboutMe}
          onChange={(e) => update("aboutMe", e.target.value)}
          className={`${inputStyle} resize-none h-24`}
          placeholder="Write something about yourself..."
          maxLength={160}
        />
        <div className="text-xs text-gray-400 mt-1 text-right">
          {draft.aboutMe?.length || 0}/160
        </div>
      </Field>

      {/* PROFILE COLOR */}
      <Field label="Profile Color">
        <div className="flex items-center gap-3">
          <input
            type="color"
            value={draft.backgroundColor || "#6366f1"}
            onChange={(e) =>
              update("backgroundColor", e.target.value)
            }
            className="w-12 h-9 rounded-lg cursor-pointer border border-gray-300 shadow-sm"
          />

          <div className="text-sm text-gray-500 font-mono">
            {draft.backgroundColor}
          </div>
        </div>
      </Field>

      {/* AVATAR */}
      <Field label="Avatar">
        <div className="flex items-center gap-4">
          <img
            src={draft.avatarUrl || "/default-avatar.png"}
            className="w-14 h-14 rounded-full object-cover shadow bg-gray-100"
          />

          <label
            className="
              text-sm cursor-pointer
              px-3 py-1.5
              rounded-lg
              bg-gray-100
              hover:bg-gray-200
              transition
            "
          >
            {uploading ? "Uploading..." : "Change avatar"}
            <input
              type="file"
              accept="image/*"
              hidden
              onChange={handleAvatarChange}
            />
          </label>
        </div>
      </Field>

      {/* STATUS MESSAGE */}
      {status && (
        <div
          className={`text-sm font-medium ${
            status.type === "success"
              ? "text-green-600"
              : "text-red-500"
          }`}
        >
          {status.message}
        </div>
      )}

      {/* SAVE BUTTON (only when dirty) */}
      {isDirty && (
        <button
          onClick={handleSave}
          disabled={saving}
          className="
            w-full py-2.5
            rounded-xl
            bg-indigo-600
            text-white
            text-sm font-medium
            hover:bg-indigo-500
            transition
            disabled:opacity-50
            shadow
          "
        >
          {saving ? "Saving..." : "Save Changes"}
        </button>
      )}
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div className="text-sm font-medium mb-1.5 text-gray-700">
        {label}
      </div>
      {children}
    </div>
  );
}