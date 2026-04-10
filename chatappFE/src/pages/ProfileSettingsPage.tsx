import { useEffect, useState } from "react";
import { changePasswordApi, getEmailVerificationStatusApi, sendVerificationEmailApi } from "../api/auth.service";
import { useAuth } from "../store/auth.store";
import ProfileEditor from "../components/profile/ProfileEditor";
import ProfilePreview from "../components/profile/ProfilePreview";
import ProfileIdentityCard from "../components/profile/ProfileIdentityCard";
import type { ProfileDraft } from "../types/profile";
import { resolveProfileBackground } from "../utils/profileBackground";
import { resolveProfilePresentation } from "../utils/profilePresentation";

export default function ProfileSettingsPage() {
  const { currentUser, refreshCurrentUser } = useAuth();

  const [draft, setDraft] = useState<ProfileDraft>({
    displayName: currentUser?.displayName ?? "",
    username: currentUser?.username ?? "",
    avatarUrl: currentUser?.avatarUrl ?? null,
    aboutMe: currentUser?.aboutMe ?? "",
    backgroundColor: resolveProfileBackground(currentUser?.backgroundColor),
  });

  const [activeSection, setActiveSection] = useState<"profile" | "security">("profile");
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [passwordErrors, setPasswordErrors] = useState<{
    currentPassword?: string;
    newPassword?: string;
    confirmPassword?: string;
  }>({});
  const [passwordStatus, setPasswordStatus] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);
  const [verificationStatus, setVerificationStatus] = useState<{
    email: string;
    verified: boolean;
  }>({
    email: currentUser?.username ?? "",
    verified: false,
  });
  const [verificationLoading, setVerificationLoading] = useState(true);
  const [verificationSending, setVerificationSending] = useState(false);
  const [verificationMessage, setVerificationMessage] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  const validatePasswordForm = () => {
    const errors: {
      currentPassword?: string;
      newPassword?: string;
      confirmPassword?: string;
    } = {};

    if (!passwordForm.currentPassword.trim()) {
      errors.currentPassword = "Current password is required.";
    }

    if (!passwordForm.newPassword) {
      errors.newPassword = "New password is required.";
    } else if (passwordForm.newPassword.length < 8) {
      errors.newPassword = "New password must be at least 8 characters.";
    } else if (!/[A-Z]/.test(passwordForm.newPassword) || !/[a-z]/.test(passwordForm.newPassword) || !/\d/.test(passwordForm.newPassword)) {
      errors.newPassword = "Use at least one uppercase letter, one lowercase letter, and one number.";
    } else if (passwordForm.newPassword === passwordForm.currentPassword) {
      errors.newPassword = "New password must be different from current password.";
    }

    if (!passwordForm.confirmPassword) {
      errors.confirmPassword = "Please confirm your new password.";
    } else if (passwordForm.confirmPassword !== passwordForm.newPassword) {
      errors.confirmPassword = "Confirm password does not match.";
    }

    setPasswordErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handlePasswordSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    setPasswordStatus(null);
    if (!validatePasswordForm()) {
      setPasswordStatus({ type: "error", message: "Please fix the highlighted password fields." });
      return;
    }

    try {
      setPasswordSaving(true);
      await changePasswordApi({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      });
      setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
      setPasswordErrors({});
      setPasswordStatus({ type: "success", message: "Password changed successfully." });
      refreshCurrentUser();
    } catch (error) {
      setPasswordStatus({
        type: "error",
        message: (error as Error).message || "Could not change password.",
      });
    } finally {
      setPasswordSaving(false);
    }
  };

  useEffect(() => {
    if (!currentUser) {
      setVerificationLoading(false);
      return;
    }

    let active = true;

    const loadVerificationStatus = async () => {
      try {
        const status = await getEmailVerificationStatusApi();
        if (!active) return;
        setVerificationStatus({
          email: status.email || currentUser.username || "",
          verified: status.verified,
        });
      } catch {
        if (!active) return;
        setVerificationStatus({
          email: currentUser.username || "",
          verified: false,
        });
      } finally {
        if (active) {
          setVerificationLoading(false);
        }
      }
    };

    void loadVerificationStatus();

    return () => {
      active = false;
    };
  }, [currentUser?.username]);

  const handleSendVerification = async () => {
    try {
      setVerificationSending(true);
      setVerificationMessage(null);
      await sendVerificationEmailApi();
      setVerificationMessage({
        type: "success",
        message: "Verification email sent. Please check your inbox.",
      });
      // Refresh status — verification state may have changed on re-send
      try {
        const status = await getEmailVerificationStatusApi();
        setVerificationStatus({ email: status.email || currentUser.username || "", verified: status.verified });
      } catch { /* ignore refresh errors */ }
    } catch (error) {
      setVerificationMessage({
        type: "error",
        message: (error as Error).message || "Could not send verification email.",
      });
    } finally {
      setVerificationSending(false);
    }
  };

  if (!currentUser) {
    return <div className="p-6">Loading profile...</div>;
  }

  const resolvedBackground = resolveProfileBackground(draft.backgroundColor);
  const presentation = resolveProfilePresentation({
    displayName: draft.displayName || currentUser.displayName,
    username: draft.username || currentUser.username,
    avatarUrl: draft.avatarUrl,
    aboutMe: draft.aboutMe,
    backgroundColor: draft.backgroundColor,
  });

  return (
    <div className="h-full w-full">
      <div className="max-w-6xl mx-auto h-full overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b bg-white p-4">
          <div className="flex flex-col lg:flex-row items-start lg:items-center gap-3">
            <h1 className="text-2xl font-semibold text-gray-900">Profile Settings</h1>
            <div className="flex gap-2">
              <button
                onClick={() => setActiveSection("profile")}
                className={`rounded-lg px-3 py-1.5 text-sm transition ${
                  activeSection === "profile"
                    ? "bg-indigo-600 text-white"
                    : "border border-gray-300 bg-white text-gray-700 hover:bg-gray-50"
                }`}
              >
                Profile
              </button>
              <button
                onClick={() => setActiveSection("security")}
                className={`rounded-lg px-3 py-1.5 text-sm transition ${
                  activeSection === "security"
                    ? "bg-indigo-600 text-white"
                    : "border border-gray-300 bg-white text-gray-700 hover:bg-gray-50"
                }`}
              >
                Security
              </button>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 p-6 gap-6">
          <aside className="space-y-4">
            <ProfileIdentityCard
              presentation={{ ...presentation, backgroundColor: resolvedBackground }}
              aboutTestId="settings-about-text"
            >
              <button className="w-full rounded-xl bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-500">
                Edit Profile
              </button>
            </ProfileIdentityCard>

            <div className="rounded-xl border border-gray-200 p-4 bg-white">
              <h2 className="text-sm font-semibold text-gray-700 mb-2">Quick Info</h2>
              <p className="text-xs text-gray-500">Customize how your profile card looks everywhere users click your avatar, name, or mentions.</p>
            </div>
          </aside>

          <main className="lg:col-span-2">
            {activeSection === "profile" && (
              <div className="space-y-6">
                <h2 className="text-xl font-semibold">Profile Settings</h2>
                <>
                  <div className="rounded-xl border border-gray-200 bg-gray-50 p-6">
                    <ProfileEditor draft={draft} setDraft={setDraft} />
                  </div>
                  <div className="mt-6 rounded-xl border border-gray-200 bg-white p-4">
                    <ProfilePreview draft={draft} />
                  </div>
                </>
              </div>
            )}

            {activeSection === "security" && (
              <div className="rounded-xl border border-gray-200 bg-white p-6">
                <h2 className="text-xl font-semibold mb-3">Security</h2>
                <p className="text-sm text-gray-600 mb-5">
                  Change your password by entering your current password and confirming the new one.
                </p>

                <form className="space-y-4" onSubmit={handlePasswordSubmit}>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Current password</label>
                    <input
                      type="password"
                      value={passwordForm.currentPassword}
                      onChange={(e) => {
                        setPasswordForm((prev) => ({ ...prev, currentPassword: e.target.value }));
                        setPasswordStatus(null);
                      }}
                      className={`w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${passwordErrors.currentPassword ? "border-red-500" : "border-gray-300"}`}
                      placeholder="Enter current password"
                      autoComplete="current-password"
                    />
                    {passwordErrors.currentPassword && (
                      <p className="mt-1 text-xs text-red-600">{passwordErrors.currentPassword}</p>
                    )}
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">New password</label>
                    <input
                      type="password"
                      value={passwordForm.newPassword}
                      onChange={(e) => {
                        setPasswordForm((prev) => ({ ...prev, newPassword: e.target.value }));
                        setPasswordStatus(null);
                      }}
                      className={`w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${passwordErrors.newPassword ? "border-red-500" : "border-gray-300"}`}
                      placeholder="Enter new password"
                      autoComplete="new-password"
                    />
                    {passwordErrors.newPassword && (
                      <p className="mt-1 text-xs text-red-600">{passwordErrors.newPassword}</p>
                    )}
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Confirm new password</label>
                    <input
                      type="password"
                      value={passwordForm.confirmPassword}
                      onChange={(e) => {
                        setPasswordForm((prev) => ({ ...prev, confirmPassword: e.target.value }));
                        setPasswordStatus(null);
                      }}
                      className={`w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${passwordErrors.confirmPassword ? "border-red-500" : "border-gray-300"}`}
                      placeholder="Confirm new password"
                      autoComplete="new-password"
                    />
                    {passwordErrors.confirmPassword && (
                      <p className="mt-1 text-xs text-red-600">{passwordErrors.confirmPassword}</p>
                    )}
                  </div>

                  {passwordStatus && (
                    <p className={`text-sm ${passwordStatus.type === "success" ? "text-green-600" : "text-red-600"}`}>
                      {passwordStatus.message}
                    </p>
                  )}

                  <button
                    type="submit"
                    disabled={passwordSaving}
                    className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:opacity-60"
                  >
                    {passwordSaving ? "Changing password..." : "Change password"}
                  </button>
                </form>

                <div className="mt-8 rounded-xl border border-gray-200 bg-gray-50 p-4">
                  <h3 className="text-lg font-semibold text-gray-900">Email verification</h3>
                  <p className="mt-1 text-sm text-gray-600">Verification status for this account email.</p>

                  <div className="mt-4 flex flex-wrap items-center gap-3">
                    <span className="text-sm font-medium text-gray-700">
                      {verificationLoading ? "Loading verification status..." : verificationStatus.email}
                    </span>
                    {!verificationLoading && (
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-semibold ${verificationStatus.verified ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}`}
                      >
                        {verificationStatus.verified ? "Verified" : "Not verified"}
                      </span>
                    )}
                  </div>

                  {verificationMessage && (
                    <p className={`mt-3 text-sm ${verificationMessage.type === "success" ? "text-green-600" : "text-red-600"}`}>
                      {verificationMessage.message}
                    </p>
                  )}

                  <button
                    type="button"
                    onClick={() => void handleSendVerification()}
                    disabled={verificationSending || verificationLoading}
                    className="mt-4 rounded-lg border border-blue-200 bg-white px-4 py-2 text-sm font-medium text-blue-700 transition hover:bg-blue-50 disabled:opacity-60"
                  >
                    {verificationSending
                      ? "Sending verification..."
                      : verificationStatus.verified
                        ? "Resend verification email"
                        : "Send verification email"}
                  </button>
                </div>
              </div>
            )}
          </main>
        </div>
      </div>
    </div>
  );
}
