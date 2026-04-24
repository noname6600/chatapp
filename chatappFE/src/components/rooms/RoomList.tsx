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
  const hasGroups = groupRoomIds.length > 0;
  const hasPrivateRooms = privateRoomIds.length > 0;
  const shouldBalanceSections =
    hasGroups && hasPrivateRooms && groupsOpen && dmsOpen;
  const groupsUsesAvailableHeight =
    hasGroups && groupsOpen && (!hasPrivateRooms || !dmsOpen);
  const dmsUsesAvailableHeight =
    dmsOpen && (shouldBalanceSections || !hasGroups || !groupsOpen);

  return (
    <div className="flex h-full min-h-0 flex-col bg-white">
      {/* Find or Start Conversation Button */}
      <div className="border-b border-slate-200/80 px-3 pb-3 pt-3">
        <button
          onClick={() => setShowConversationModal(true)}
          className="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-blue-500 via-blue-600 to-cyan-500 px-4 py-3 text-sm font-medium text-white transition-all hover:from-blue-600 hover:via-blue-700 hover:to-cyan-600 hover:shadow-md"
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
        <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-hidden px-3 py-3">
          {/* Groups Section */}
          {hasGroups && (
            <section
              data-testid="room-list-groups-section"
              className={`flex flex-col rounded-2xl border border-slate-200/80 bg-slate-50/80 shadow-sm shadow-slate-200/50 ${
                shouldBalanceSections
                  ? "min-h-0 max-h-[45%] shrink-0"
                  : groupsUsesAvailableHeight
                    ? "min-h-0 flex-1"
                    : "shrink-0"
              }`}
            >
              <button
                type="button"
                aria-expanded={groupsOpen}
                onClick={() => setGroupsOpen((o) => !o)}
                className="flex w-full items-center gap-2 rounded-2xl px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500 transition-colors hover:bg-white/70"
              >
                {groupsOpen ? (
                  <ChevronDown size={14} className="shrink-0 text-slate-400" />
                ) : (
                  <ChevronRight size={14} className="shrink-0 text-slate-400" />
                )}
                <span>Groups</span>
                <span className="ml-auto rounded-full bg-white px-2 py-0.5 text-[11px] font-medium normal-case tracking-normal text-slate-500 ring-1 ring-slate-200">
                  {groupRoomIds.length}
                </span>
              </button>

              {groupsOpen && (
                <div
                  data-testid="room-list-groups-body"
                  className={`min-h-0 px-2 pb-2 ${
                    shouldBalanceSections || groupsUsesAvailableHeight
                      ? "overflow-y-auto"
                      : "overflow-visible"
                  }`}
                >
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
            </section>
          )}

          {/* Direct Messages Section */}
          <section
            data-testid="room-list-dms-section"
            className={`flex min-h-0 flex-col rounded-2xl border border-slate-200/80 bg-white shadow-sm shadow-slate-200/50 ${
              dmsUsesAvailableHeight ? "flex-1" : "shrink-0"
            }`}
          >
            <button
              type="button"
              aria-expanded={dmsOpen}
              onClick={() => setDmsOpen((o) => !o)}
              className="flex w-full items-center gap-2 rounded-2xl px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500 transition-colors hover:bg-slate-50"
            >
              {dmsOpen ? (
                <ChevronDown size={14} className="shrink-0 text-slate-400" />
              ) : (
                <ChevronRight size={14} className="shrink-0 text-slate-400" />
              )}
              <span>Direct Messages</span>
              <span className="ml-auto rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium normal-case tracking-normal text-slate-500 ring-1 ring-slate-200/80">
                {privateRoomIds.length}
              </span>
            </button>

            {dmsOpen && (
              <div
                data-testid="room-list-dms-body"
                className={`min-h-0 px-2 pb-2 ${
                  dmsUsesAvailableHeight ? "flex-1 overflow-y-auto" : ""
                }`}
              >
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
                  <div className="flex items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/80 px-4 py-8 text-center">
                    <div className="space-y-1">
                      <p className="text-sm text-slate-500">No direct messages yet.</p>
                      <p className="text-xs text-slate-400">
                        Use the button above to start a conversation!
                      </p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </section>
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
