import { useCallback, useEffect, useState } from "react";
import { X, Upload } from "lucide-react";
import { updateRoomApi, uploadRoomAvatarApi } from "../../api/room.service";
import { useRooms } from "../../store/room.store";
import { useNotifications } from "../../store/notification.store";
import type { Room } from "../../types/room";

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
  const { mutesByRoom, fetchRoomMute, toggleRoomMute } = useNotifications();
  const [roomName, setRoomName] = useState(room.name);
  const [previewImage, setPreviewImage] = useState<string | null>(
    room.avatarUrl || null
  );
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [muteLoading, setMuteLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const isGroupRoom = room.type === "GROUP";
  const isMuted = Boolean(mutesByRoom[room.id]);

  // Auto-clear success message after 3 seconds
  useEffect(() => {
    if (success) {
      const timer = setTimeout(() => setSuccess(false), 3000);
      return () => clearTimeout(timer);
    }
  }, [success]);

  useEffect(() => {
    if (!isOpen || !isGroupRoom) return;

    void fetchRoomMute(room.id).catch(() => {});
  }, [fetchRoomMute, isGroupRoom, isOpen, room.id]);

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

  const handleMuteToggle = useCallback(async () => {
    if (!isGroupRoom || muteLoading) return;

    setMuteLoading(true);
    setError(null);

    try {
      await toggleRoomMute(room.id, !isMuted);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update mute setting");
    } finally {
      setMuteLoading(false);
    }
  }, [isGroupRoom, isMuted, muteLoading, room.id, toggleRoomMute]);

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
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">
                  Notifications
                </label>

                <button
                  type="button"
                  onClick={() => {
                    void handleMuteToggle();
                  }}
                  disabled={loading || muteLoading}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-left text-sm text-gray-800 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <span className="font-medium">Mute this group</span>
                  <span className="ml-2 text-xs text-gray-500">
                    {isMuted ? "On" : "Off"}
                  </span>
                </button>
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
