import { useCallback, useMemo, useState } from "react";
import { ChevronDown, ChevronRight, MessageSquarePlus } from "lucide-react";
import { useChat } from "../../store/chat.store";
import { useRooms } from "../../store/room.store";
import { getSplitRoomSections } from "../../utils/roomListIntegrity";
import ConversationModal from "./ConversationModal";
import GroupRoomItem from "./GroupRoomItem";
import PrivateRoomItem from "./PrivateRoomItem";

export default function RoomList() {
  const [showConversationModal, setShowConversationModal] = useState(false);
  const [groupsOpen, setGroupsOpen] = useState(true);
  const [dmsOpen, setDmsOpen] = useState(true);
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
    <div className="flex flex-col h-full bg-white">
      {/* Find or Start Conversation Button */}
      <div className="px-3 pt-3 pb-2">
        <button
          onClick={() => setShowConversationModal(true)}
          className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-gradient-to-r from-blue-500 to-blue-600 text-white rounded-xl font-medium text-sm hover:from-blue-600 hover:to-blue-700 transition-all shadow-sm hover:shadow-md"
        >
          <MessageSquarePlus size={16} />
          Find or start a conversation
        </button>
      </div>

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
        <div className="flex-1 overflow-hidden flex flex-col border-t border-gray-100">

          {/* Groups Section */}
          {groupRoomIds.length > 0 && (
            <div className="flex flex-col border-b border-gray-100">
              {/* Groups Header */}
              <button
                onClick={() => setGroupsOpen((o) => !o)}
                className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wider hover:bg-gray-50 transition-colors w-full text-left select-none"
              >
                {groupsOpen ? (
                  <ChevronDown size={13} className="text-gray-400 flex-shrink-0" />
                ) : (
                  <ChevronRight size={13} className="text-gray-400 flex-shrink-0" />
                )}
                <span>Groups</span>
                <span className="ml-auto text-gray-400 font-normal normal-case tracking-normal">
                  {groupRoomIds.length}
                </span>
              </button>

              {/* Groups Content */}
              {groupsOpen && (
                <div className="flex flex-col overflow-y-auto">
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
            </div>
          )}

          {/* Direct Messages Section */}
          <div className="flex flex-col flex-1 min-h-0">
            {/* DMs Header */}
            <button
              onClick={() => setDmsOpen((o) => !o)}
              className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wider hover:bg-gray-50 transition-colors w-full text-left select-none flex-shrink-0"
            >
              {dmsOpen ? (
                <ChevronDown size={13} className="text-gray-400 flex-shrink-0" />
              ) : (
                <ChevronRight size={13} className="text-gray-400 flex-shrink-0" />
              )}
              <span>Direct Messages</span>
              <span className="ml-auto text-gray-400 font-normal normal-case tracking-normal">
                {privateRoomIds.length}
              </span>
            </button>

            {/* DMs Content */}
            {dmsOpen && (
              <div className="flex-1 overflow-y-auto">
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
                  <div className="flex items-center justify-center px-4 py-8 text-center">
                    <div className="space-y-1">
                      <p className="text-gray-400 text-sm">No direct messages yet.</p>
                      <p className="text-gray-300 text-xs">
                        Use the button above to start a conversation!
                      </p>
                    </div>
                  </div>
                )}
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
