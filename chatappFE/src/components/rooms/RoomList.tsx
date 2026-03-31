import { useCallback, useMemo, useState } from "react";
import { MessageSquarePlus } from "lucide-react";
import { useChat } from "../../store/chat.store";
import { useRooms } from "../../store/room.store";
import { getSplitRoomSections } from "../../utils/roomListIntegrity";
import ConversationModal from "./ConversationModal";
import GroupRoomItem from "./GroupRoomItem";
import PrivateRoomItem from "./PrivateRoomItem";

export default function RoomList() {
  const [showConversationModal, setShowConversationModal] = useState(false);
  const { activeRoomId, setActiveRoom } = useChat();
  const { roomsById, roomOrder } = useRooms();

  const { groupRoomIds, privateRoomIds } = useMemo(
    () => getSplitRoomSections(roomsById, roomOrder),
    [roomsById, roomOrder]
  );

  const handleRoomClick = useCallback(
    (roomId: string) => {
      setActiveRoom(roomId);
    },
    [setActiveRoom]
  );

  const hasNoRooms = groupRoomIds.length === 0 && privateRoomIds.length === 0;

  return (
    <div className="flex flex-col h-full">
      {/* Find or Start Conversation Button */}
      <button
        onClick={() => setShowConversationModal(true)}
        className="m-3 flex items-center justify-center gap-2 px-4 py-3 bg-gradient-to-r from-blue-500 to-blue-600 text-white rounded-lg font-medium hover:from-blue-600 hover:to-blue-700 transition shadow-md"
      >
        <MessageSquarePlus size={18} />
        Find or start a conversation
      </button>

      {/* Empty State */}
      {hasNoRooms && (
        <div className="flex-1 flex items-center justify-center px-4 py-8 text-center">
          <div className="space-y-2">
            <p className="text-gray-500 text-sm">No conversations yet.</p>
            <p className="text-gray-400 text-xs">
              Click the button above to create a group or send a message!
            </p>
          </div>
        </div>
      )}

      {/* Rooms List */}
      {!hasNoRooms && (
        <div className="flex-1 overflow-visible flex border-t">
          {groupRoomIds.length > 0 && (
            <div className="flex flex-col items-center gap-2 p-3 border-r bg-gray-50 overflow-y-auto overflow-x-hidden relative z-10">
              {groupRoomIds.map((roomId) => {
                const room = roomsById[roomId];
                if (!room) return null;

                return (
                  <GroupRoomItem
                    key={roomId}
                    room={room}
                    isActive={activeRoomId === roomId}
                    onClick={() => handleRoomClick(roomId)}
                  />
                );
              })}
            </div>
          )}

          <div className="flex-1 flex flex-col overflow-y-auto">
            {privateRoomIds.length > 0 ? (
              privateRoomIds.map((roomId) => {
                const room = roomsById[roomId];
                if (!room) return null;

                return (
                  <PrivateRoomItem
                    key={roomId}
                    room={room}
                    isActive={activeRoomId === roomId}
                    onClick={() => handleRoomClick(roomId)}
                  />
                );
              })
            ) : (
              <div className="flex-1 flex items-center justify-center px-4 py-8 text-center">
                <div className="space-y-2">
                  <p className="text-gray-500 text-sm">
                    No private messages yet.
                  </p>
                  <p className="text-gray-400 text-xs">
                    Use the button above to start a conversation!
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Conversation Modal */}
      <ConversationModal
        isOpen={showConversationModal}
        onClose={() => setShowConversationModal(false)}
        onSelectRoom={handleRoomClick}
      />
    </div>
  );
}
