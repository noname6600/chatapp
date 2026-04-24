import { useCallback, useEffect, useRef, useState } from "react";
import { Check, ChevronDown, X, Upload } from "lucide-react";
import { updateRoomApi, uploadRoomAvatarApi } from "../../api/room.service";
import { useRooms } from "../../store/room.store";
import { useNotifications } from "../../store/notification.store";
import type { Room } from "../../types/room";
import type { RoomNotificationMode } from "../../types/notification";

interface RoomSettingsModalProps {
  isOpen: boolean;
  room: Room;
  onClose: () => void;
}

export default function RoomSettingsModal({
  isOpen,
  room,
  onClose,
}: RoomSettingsModalProps) {
  const { loadRooms } = useRooms();
  const { notificationModesByRoom, fetchRoomNotificationMode, setRoomNotificationMode } = useNotifications();
  const [roomName, setRoomName] = useState(room.name);
  const [previewImage, setPreviewImage] = useState<string | null>(
    room.avatarUrl || null
  );
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [muteLoading, setMuteLoading] = useState(false);
  const [showMuteMenu, setShowMuteMenu] = useState(false);
  const [muteMenuOpenUpward, setMuteMenuOpenUpward] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const muteMenuRef = useRef<HTMLDivElement | null>(null);
  const muteMenuDropdownRef = useRef<HTMLDivElement | null>(null);

  const isGroupRoom = room.type === "GROUP";
  const currentMode = notificationModesByRoom[room.id] ?? "NO_RESTRICT";

  const modeSummary =
    currentMode === "NO_RESTRICT"
      ? "No restrict"
      : currentMode === "ONLY_MENTION"
        ? "Only mentions"
        : "Nothing";

  // Auto-clear success message after 3 seconds
  useEffect(() => {
    if (success) {
      const timer = setTimeout(() => setSuccess(false), 3000);
      return () => clearTimeout(timer);
    }
  }, [success]);

  useEffect(() => {
    if (!isOpen || !isGroupRoom) return;

    void fetchRoomNotificationMode(room.id).catch(() => {});
  }, [fetchRoomNotificationMode, isGroupRoom, isOpen, room.id]);

  useEffect(() => {
    if (!isOpen) {
      setShowMuteMenu(false);
    }
  }, [isOpen]);

  useEffect(() => {
    if (!showMuteMenu) return;

    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (!muteMenuRef.current?.contains(target)) {
        setShowMuteMenu(false);
      }
    };

    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, [showMuteMenu]);

  useEffect(() => {
    if (!showMuteMenu) return;

    const updateMenuDirection = () => {
      const menuRoot = muteMenuRef.current;
      const dropdown = muteMenuDropdownRef.current;
      if (!menuRoot || !dropdown) return;

      const rootRect = menuRoot.getBoundingClientRect();
      const dropdownHeight = dropdown.offsetHeight || 260;
      const spaceBelow = window.innerHeight - rootRect.bottom;
      const spaceAbove = rootRect.top;

      setMuteMenuOpenUpward(spaceBelow < dropdownHeight + 16 && spaceAbove > spaceBelow);
    };

    updateMenuDirection();
    window.addEventListener("resize", updateMenuDirection);
    window.addEventListener("scroll", updateMenuDirection, true);

    return () => {
      window.removeEventListener("resize", updateMenuDirection);
      window.removeEventListener("scroll", updateMenuDirection, true);
    };
  }, [showMuteMenu]);

  const handleImageSelect = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (!file) return;

      setSelectedFile(file);
      const reader = new FileReader();
      reader.onload = (event) => {
        setPreviewImage(event.target?.result as string);
      };
      reader.readAsDataURL(file);
    },
    []
  );

  const handleSave = useCallback(async () => {
    if (!roomName.trim()) {
      setError("Room name cannot be empty");
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(false);

    try {
      // Update room name if changed
      if (roomName !== room.name) {
        await updateRoomApi(room.id, { name: roomName });
      }

      // Upload new avatar if selected
      if (selectedFile) {
        await uploadRoomAvatarApi(room.id, selectedFile);
      }

      // Reload rooms to get updated data
      await loadRooms();

      // Show success message but keep modal open
      setSuccess(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update room");
    } finally {
      setLoading(false);
    }
  }, [roomName, room.id, room.name, selectedFile, loadRooms]);

  const handleModeChange = useCallback(async (mode: RoomNotificationMode) => {
    if (!isGroupRoom || muteLoading) return;

    setMuteLoading(true);
    setError(null);

    try {
      await setRoomNotificationMode(room.id, mode);
      setShowMuteMenu(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update notification setting");
    } finally {
      setMuteLoading(false);
    }
  }, [isGroupRoom, muteLoading, room.id, setRoomNotificationMode]);

  if (!isOpen) return null;

  return (
    <>
      {/* Modal Backdrop */}
      <div className="fixed inset-0 bg-black/50 z-40" onClick={onClose} />

      {/* Modal */}
      <div className="fixed inset-0 flex items-center justify-center z-50 p-4">
        <div
          className="bg-white rounded-lg shadow-lg w-full max-w-md"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-4 border-b">
            <h2 className="text-lg font-semibold text-gray-900">
              Group Settings
            </h2>
            <button
              onClick={onClose}
              className="p-1 text-gray-400 hover:text-gray-600 transition"
            >
              <X size={20} />
            </button>
          </div>

          {/* Content */}
          <div className="p-4 space-y-6">
            {/* Room Avatar Section */}
            <div className="space-y-3">
              <label className="block text-sm font-medium text-gray-700">
                Group Avatar
              </label>

              {/* Preview */}
              <div className="flex justify-center mb-3">
                <img
                  src={previewImage || "/default-avatar.png"}
                  alt="Room avatar"
                  className="w-24 h-24 rounded-lg object-cover border-2 border-gray-200"
                />
              </div>

              {/* Upload Button */}
              <label className="flex items-center justify-center w-full px-4 py-2 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-blue-500 hover:bg-blue-50 transition">
                <div className="flex items-center gap-2 text-gray-600">
                  <Upload size={18} />
                  <span className="text-sm font-medium">
                    {selectedFile ? "Change Image" : "Upload Image"}
                  </span>
                </div>
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleImageSelect}
                  className="hidden"
                  disabled={loading}
                />
              </label>

              {selectedFile && (
                <p className="text-xs text-gray-500">
                  Selected: {selectedFile.name}
                </p>
              )}
            </div>

            {/* Room Name Section */}
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">
                Group Name
              </label>
              <input
                type="text"
                value={roomName}
                onChange={(e) => setRoomName(e.target.value)}
                disabled={loading}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 disabled:opacity-50"
                maxLength={50}
              />
              <p className="text-xs text-gray-500 text-right">
                {roomName.length}/50
              </p>
            </div>

            {isGroupRoom && (
              <div className="space-y-3">
                <label className="block text-sm font-medium text-gray-700">
                  Notifications
                </label>

                <div ref={muteMenuRef} className="relative">
                  <button
                    type="button"
                    onClick={() => setShowMuteMenu((prev) => !prev)}
                    disabled={loading || muteLoading}
                    aria-haspopup="menu"
                    aria-expanded={showMuteMenu}
                    aria-label="Mute notifications"
                    className="w-full rounded-xl border border-gray-200 bg-white px-3 py-3 text-left transition hover:border-blue-300 hover:bg-blue-50/40 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div>
                        <p className="text-sm font-semibold text-gray-800">Mute</p>
                        <p className="mt-1 text-xs text-gray-600">
                          Choose how this group should notify you.
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="rounded-full bg-gray-100 px-2 py-1 text-xs font-semibold text-gray-700">
                          {modeSummary}
                        </span>
                        <ChevronDown size={16} className="text-gray-500" />
                      </div>
                    </div>
                  </button>

                  {showMuteMenu && (
                    <div
                      ref={muteMenuDropdownRef}
                      className={`absolute right-0 z-20 w-[20rem] max-h-[min(18rem,calc(100vh-6rem))] overflow-y-auto rounded-xl border border-gray-200 bg-white p-1 shadow-lg ${
                        muteMenuOpenUpward ? "bottom-full mb-2" : "top-full mt-2"
                      }`}
                    >
                      {[
                        {
                          mode: "NO_RESTRICT" as RoomNotificationMode,
                          title: "No restrict",
                          description: "Unread badge updates for new group messages. Only @mentions send alerts.",
                        },
                        {
                          mode: "ONLY_MENTION" as RoomNotificationMode,
                          title: "Only mentions",
                          description: "Only messages that mention you will appear as unread or notify.",
                        },
                        {
                          mode: "NOTHING" as RoomNotificationMode,
                          title: "Nothing",
                          description: "Completely mute this group. No unread badge and no notifications.",
                        },
                      ].map((option) => {
                        const selected = currentMode === option.mode;
                        return (
                          <button
                            key={option.mode}
                            type="button"
                            onClick={() => {
                              void handleModeChange(option.mode);
                            }}
                            disabled={loading || muteLoading}
                            aria-label={`${option.title} notification mode`}
                            className={`flex w-full items-start gap-2 rounded-lg px-3 py-2 text-left transition disabled:cursor-not-allowed disabled:opacity-50 ${
                              selected ? "bg-blue-50" : "hover:bg-gray-50"
                            }`}
                          >
                            <span className="mt-0.5 h-4 w-4 shrink-0 text-blue-600">
                              {selected ? <Check size={16} /> : null}
                            </span>
                            <span className="min-w-0">
                              <span className={`block text-sm font-semibold ${selected ? "text-blue-700" : "text-gray-800"}`}>
                                {option.title}
                              </span>
                              <span className="mt-0.5 block text-xs text-gray-600">{option.description}</span>
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Error Message */}
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded text-sm text-red-700">
                {error}
              </div>
            )}

            {/* Success Message */}
            {success && (
              <div className="p-3 bg-green-50 border border-green-200 rounded text-sm text-green-700">
                Settings updated successfully!
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex gap-2 p-4 border-t">
            <button
              onClick={onClose}
              disabled={loading}
              className="flex-1 px-4 py-2 border border-gray-300 text-gray-900 rounded-lg hover:bg-gray-50 transition disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={loading}
              className="flex-1 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Saving...
                </>
              ) : (
                "Save Changes"
              )}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
