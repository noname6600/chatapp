import { useState } from "react";
import { X } from "lucide-react";
import { Button } from "../ui/Button";
import { joinRoomByCodeApi } from "../../api/room.service";
import { useRooms } from "../../store/room.store";
import { preserveJoinCodeInput } from "../../utils/roomCodeIntegrity";

interface JoinRoomModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: (roomId: string) => void;
}

export default function JoinRoomModal({
  isOpen,
  onClose,
}: JoinRoomModalProps) {
  const [roomCode, setRoomCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { loadRooms } = useRooms();

  const handleJoin = async () => {
    if (!roomCode.trim()) {
      setError("Room code is required");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await joinRoomByCodeApi(roomCode.trim());
      setRoomCode("");
      await loadRooms();
      onClose();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to join room"
      );
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !loading) {
      handleJoin();
    }
    if (e.key === "Escape") {
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">Join Room</h2>
          <button
            onClick={onClose}
            disabled={loading}
            className="p-1 hover:bg-gray-100 rounded transition"
            type="button"
          >
            <X size={20} />
          </button>
        </div>

        <div className="space-y-2">
          <label className="block text-sm font-medium text-gray-700">
            Room Code
          </label>
          <input
            type="text"
            value={roomCode}
            onChange={(e) => {
              // Keep exactly what the user typed. Do not force case normalization.
              setRoomCode(preserveJoinCodeInput(e.target.value));
              setError(null);
            }}
            onKeyDown={handleKeyDown}
            placeholder="Enter room code..."
            disabled={loading}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 font-mono"
            autoFocus
          />
          <p className="text-xs text-gray-500">
            Ask the room creator for the room code.
          </p>
        </div>

        {error && (
          <div className="px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="flex gap-3 justify-end">
          <Button
            variant="outline"
            onClick={onClose}
            disabled={loading}
            size="sm"
          >
            Cancel
          </Button>
          <Button
            onClick={handleJoin}
            disabled={loading || !roomCode.trim()}
            size="sm"
          >
            {loading ? "Joining..." : "Join"}
          </Button>
        </div>
      </div>
    </div>
  );
}
