import { useEffect, useState } from "react";
import RoomList from "../components/rooms/RoomList";
import RoomHeader from "../components/chat/RoomHeader";
import MessageList from "../components/chat/MessageList";
import MessageInput from "../components/chat/MessageInput";
import RoomMembersSidebar from "../components/chat/RoomMembersSidebar";
import TypingIndicator from "../components/presence/TypingIndicator";
import InviteMembersModal from "../components/rooms/InviteMembersModal";
import RoomSettingsModal from "../components/rooms/RoomSettingsModal";
import LeaveGroupModal from "../components/rooms/LeaveGroupModal";

import {
  onPresenceOpen,
  joinPresenceRoom,
  leavePresenceRoom,
} from "../websocket/presence.socket";

import { useChat } from "../store/chat.store";
import { useRooms } from "../store/room.store";
import { useNotifications } from "../store/notification.store";

export default function ChatPageLayout() {
  const { activeRoomId, setActiveRoom } = useChat();
  const { roomsById, loadRooms } = useRooms();
  const { fetchRoomMute } = useNotifications();
  const [showInviteModal, setShowInviteModal] = useState(false);
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [showLeaveModal, setShowLeaveModal] = useState(false);

  const currentRoom = activeRoomId ? roomsById[activeRoomId] : null;

  useEffect(() => {
    if (!activeRoomId) return;

    joinPresenceRoom(activeRoomId);
    return () => leavePresenceRoom(activeRoomId);
  }, [activeRoomId]);

  useEffect(() => {
    const unsub = onPresenceOpen(() => {
      if (activeRoomId) joinPresenceRoom(activeRoomId);
    });

    return unsub;
  }, [activeRoomId]);

  useEffect(() => {
    if (!activeRoomId) return;
    if (currentRoom?.type !== "GROUP") return;

    void fetchRoomMute(activeRoomId).catch(() => {});
  }, [activeRoomId, currentRoom?.type, fetchRoomMute]);

  const handleLeaveSuccess = async () => {
    await loadRooms();
    setActiveRoom("");
  };

  return (
    <div className="flex h-full min-h-0 overflow-hidden">
      <div className="w-72 shrink-0 border-r bg-white overflow-hidden">
        <RoomList />
      </div>

      <div className="flex flex-col flex-1 min-h-0 min-w-0 bg-white overflow-hidden">
        {!activeRoomId ? (
          <div className="flex flex-1 items-center justify-center text-gray-500">
            Select a room to start chatting
          </div>
        ) : (
          <>
            <RoomHeader
              room={currentRoom}
              onInvite={() => setShowInviteModal(true)}
              onSettings={() => setShowSettingsModal(true)}
              onLeave={() => setShowLeaveModal(true)}
            />
            <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
              <MessageList roomId={activeRoomId} />
            </div>
            <TypingIndicator roomId={activeRoomId} />
            <MessageInput roomId={activeRoomId} />
          </>
        )}
      </div>

      {activeRoomId && (
        <div className="w-72 shrink-0 border-l bg-white overflow-hidden">
          <RoomMembersSidebar roomId={activeRoomId} />
        </div>
      )}

      {/* Modals */}
      {currentRoom && (
        <>
          <InviteMembersModal
            isOpen={showInviteModal}
            room={currentRoom}
            onClose={() => setShowInviteModal(false)}
          />
          <RoomSettingsModal
            isOpen={showSettingsModal}
            room={currentRoom}
            onClose={() => setShowSettingsModal(false)}
          />
          <LeaveGroupModal
            isOpen={showLeaveModal}
            room={currentRoom}
            onClose={() => setShowLeaveModal(false)}
            onSuccess={handleLeaveSuccess}
          />
        </>
      )}
    </div>
  );
}