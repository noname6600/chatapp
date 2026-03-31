import { useState } from "react";
import { sendFriendRequestApi } from "../../api/friend.service";

export function AddFriendPanel() {
  const [username, setUsername] = useState("");
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const handleAdd = async () => {
    if (!username.trim()) return;

    try {
      setLoading(true);
      setMsg(null);

      await sendFriendRequestApi(username.trim());

      setMsg("✅ Friend request sent");
      setUsername("");
    } catch (e) {
      setMsg("❌ Cannot send request");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md bg-gray-100 p-4 rounded">
      <div className="font-semibold mb-2">Add Friend</div>

      <div className="flex gap-2">
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="Enter username..."
          className="flex-1 px-3 py-2 rounded border outline-none"
        />

        <button
          onClick={handleAdd}
          disabled={loading}
          className="
            px-4 py-2
            rounded
            bg-blue-500 text-white
            hover:bg-blue-600
            disabled:opacity-50
          "
        >
          Add
        </button>
      </div>

      {msg && (
        <div className="text-sm mt-2 text-gray-600">
          {msg}
        </div>
      )}
    </div>
  );
}