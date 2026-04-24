import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import RoomList from "../components/rooms/RoomList";
import RoomHeader from "../components/chat/RoomHeader";
import MessageList from "../components/chat/MessageList";
import MessageInput from "../components/chat/MessageInput";
import RoomMembersSidebar from "../components/chat/RoomMembersSidebar";
import TypingIndicator from "../components/presence/TypingIndicator";
import RoomSettingsModal from "../components/rooms/RoomSettingsModal";
import LeaveGroupModal from "../components/rooms/LeaveGroupModal";
import InviteMembersModal from "../components/rooms/InviteMembersModal";
import PinnedMessagesPanel from "../components/chat/PinnedMessagesPanel";
import ConfirmUnpinDialog from "../components/chat/ConfirmUnpinDialog";
import { sendMessageApi, unpinMessage } from "../api/chat.service";
import { startPrivateChatApi } from "../api/room.service";

import {
  onPresenceOpen,
  joinPresenceRoom,
  leavePresenceRoom,
} from "../websocket/presence.socket";

import { useChat } from "../store/chat.store";
import { useRooms } from "../store/room.store";
import { useNotifications } from "../store/notification.store";
import { useInviteJoin } from "../hooks/useInviteJoin";
import { usePinnedMessages } from "../hooks/usePinnedMessages";
import { useUnpin } from "../hooks/useUnpin";
import type { PinnedMessage } from "../types/message";

export default function ChatPageLayout() {
  const { activeRoomId, setActiveRoom, upsertMessage } = useChat();
  const { roomsById, loadRooms, removeRoom } = useRooms();
  const { fetchRoomNotificationMode } = useNotifications();
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [showLeaveModal, setShowLeaveModal] = useState(false);
  const [showInviteModal, setShowInviteModal] = useState(false);
  const [showMembers, setShowMembers] = useState(true);
  const [showPinsPanel, setShowPinsPanel] = useState(false);

  // ── Deep-link invite join (/chat?join=<roomId>) ──────────────────────────
  const [searchParams, setSearchParams] = useSearchParams();
  const deepLinkRoomId = searchParams.get("join");
  const { lifecycle: joinLifecycle, failureReason: joinFailureReason, isRetryable: joinIsRetryable, joinRoom } = useInviteJoin();
  // Stores the room ID being joined so the retry button works after URL param is cleared.
  const joinTargetRoomIdRef = useRef<string | null>(null);
  const handledJoinIdRef = useRef<string | null>(null);

  useEffect(() => {
    if (!deepLinkRoomId) return;
    if (handledJoinIdRef.current === deepLinkRoomId) return;
    handledJoinIdRef.current = deepLinkRoomId;
    joinTargetRoomIdRef.current = deepLinkRoomId;
    // Clear the ?join= param immediately so it is not re-processed on re-renders.
    setSearchParams({}, { replace: true });
    void joinRoom(deepLinkRoomId);
  }, [deepLinkRoomId, joinRoom, setSearchParams]);

  const currentRoom = activeRoomId ? roomsById[activeRoomId] : null;
  const currentRoomId = currentRoom?.id ?? null;
  const { pinnedMessages, pinnedCount, refreshPinnedMessages } = usePinnedMessages(currentRoomId);
  const pinnedMessageIds = new Set(pinnedMessages.map((message) => message.messageId));
  const { unpinningMessageId, unpinningMessagePreview, setUnpinning, clearUnpinning } = useUnpin();

  const handleSendInviteCard = async (targetUserId: string) => {
    if (!currentRoom || currentRoom.type !== "GROUP") return;

    const privateRoom = await startPrivateChatApi(targetUserId);

    const message = await sendMessageApi({
      roomId: privateRoom.id,
      blocks: [
        {
          type: "ROOM_INVITE",
          roomInvite: {
            roomId: currentRoom.id,
            roomName: currentRoom.name,
            roomAvatarUrl: currentRoom.avatarUrl ?? undefined,
          },
        },
      ],
    });

    if (activeRoomId === privateRoom.id) {
      upsertMessage(message);
    }

    await loadRooms();
  };

  useEffect(() => {
    setShowInviteModal(false);
  }, [currentRoomId]);

  useEffect(() => {
    setShowPinsPanel(false);
    clearUnpinning();
  }, [currentRoomId, clearUnpinning]);

  useEffect(() => {
    const handler = (event: Event) => {
      const customEvent = event as CustomEvent<{ roomId: string }>;
      if (!customEvent.detail) return;
      if (!currentRoomId || customEvent.detail.roomId !== currentRoomId) return;
      setShowPinsPanel(true);
    };

    window.addEventListener("chat:open-pins-panel", handler as EventListener);
    return () => {
      window.removeEventListener("chat:open-pins-panel", handler as EventListener);
    };
  }, [currentRoomId]);

  const handleJumpToPinnedMessage = (messageId: string) => {
    if (!currentRoomId) return;
    setShowPinsPanel(false);
    window.dispatchEvent(
      new CustomEvent("chat:jump-to-message", {
        detail: { roomId: currentRoomId, messageId },
      })
    );
  };

  const handleUnpinConfirm = async (messageId: string) => {
    if (!currentRoomId) return;
    try {
      await unpinMessage(currentRoomId, messageId);
      await refreshPinnedMessages();
      clearUnpinning();
    } catch (error) {
      console.error("Unpin failed:", error);
    }
  };

  const handleRequestUnpin = (message: PinnedMessage) => {
    setUnpinning(
      message.messageId,
      (message.content || "").trim() || "(attachment or structured content)"
    );
  };

  useEffect(() => {
    if (!currentRoomId) return;

    joinPresenceRoom(currentRoomId);
    return () => leavePresenceRoom(currentRoomId);
  }, [currentRoomId]);

  useEffect(() => {
    const unsub = onPresenceOpen(() => {
      if (currentRoomId) joinPresenceRoom(currentRoomId);
    });

    return unsub;
  }, [currentRoomId]);

  useEffect(() => {
    if (!currentRoomId) return;
    if (currentRoom?.type !== "GROUP") return;

    void fetchRoomNotificationMode(currentRoomId).catch(() => {});
  }, [currentRoomId, currentRoom?.type, fetchRoomNotificationMode]);

  const handleLeaveSuccess = async (roomId: string) => {
    setShowLeaveModal(false);
    setShowSettingsModal(false);
    setShowInviteModal(false);
    leavePresenceRoom(roomId);
    removeRoom(roomId);

    if (activeRoomId === roomId) {
      await setActiveRoom("");
    }

    await loadRooms();
  };

  return (
    <div className="flex h-full min-h-0 overflow-hidden">
      <div className="w-72 shrink-0 border-r bg-white overflow-hidden">
        <RoomList />
      </div>

      <div className="flex flex-col flex-1 min-h-0 min-w-0 bg-white overflow-hidden">
        {!currentRoom ? (
          <div className="flex flex-1 items-center justify-center text-gray-500">
            {joinLifecycle === "joining" ? (
              <p>Joining group...</p>
            ) : joinLifecycle === "failed" ? (
              <div className="flex flex-col items-center gap-3">
                <p className="text-red-600 font-medium">
                  {joinFailureReason === "invalid"
                    ? "This invite link is no longer valid."
                    : joinFailureReason === "already-member"
                    ? "You are already a member of this group."
                    : "Could not join — please try again."}
                </p>
                {joinIsRetryable && joinTargetRoomIdRef.current && (
                  <button
                    type="button"
                    className="text-sm text-blue-600 underline"
                    onClick={() => void joinRoom(joinTargetRoomIdRef.current!)}
                  >
                    Retry
                  </button>
                )}
              </div>
            ) : (
              <span>Select a room to start chatting</span>
            )}
          </div>
        ) : (
          <>
            <RoomHeader
              room={currentRoom}
              onInvite={() => setShowInviteModal(true)}
              onSettings={() => setShowSettingsModal(true)}
              onLeave={() => setShowLeaveModal(true)}
              membersOpen={showMembers}
              onToggleMembers={() => setShowMembers((v) => !v)}
              pinsOpen={showPinsPanel}
              pinCount={pinnedCount}
              onTogglePins={() => setShowPinsPanel((v) => !v)}
            />
            <div className="relative flex flex-col flex-1 min-h-0 overflow-hidden">
              <MessageList roomId={currentRoom.id} pinnedMessageIds={pinnedMessageIds} />
              <PinnedMessagesPanel
                open={showPinsPanel}
                pinnedMessages={pinnedMessages}
                onClose={() => setShowPinsPanel(false)}
                onJumpToMessage={handleJumpToPinnedMessage}
                onRequestUnpin={handleRequestUnpin}
              />
            </div>
            <TypingIndicator roomId={currentRoom.id} />
            <MessageInput roomId={currentRoom.id} />
          </>
        )}
      </div>

      {currentRoom && showMembers && (
        <div className="w-60 shrink-0 border-l bg-white overflow-hidden">
          <RoomMembersSidebar roomId={currentRoom.id} />
        </div>
      )}

      {/* Modals */}
      {currentRoom && (
        <>
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
          <InviteMembersModal
            isOpen={showInviteModal}
            room={currentRoom}
            onClose={() => setShowInviteModal(false)}
            onInviteUser={handleSendInviteCard}
          />
          {unpinningMessageId && unpinningMessagePreview != null && (
            <ConfirmUnpinDialog
              messageId={unpinningMessageId}
              messagePreview={unpinningMessagePreview}
              onConfirm={handleUnpinConfirm}
              onCancel={clearUnpinning}
            />
          )}
        </>
      )}
    </div>
  );
}
