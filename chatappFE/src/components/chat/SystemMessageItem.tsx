import Username from "../user/Username"
import type { ChatMessage } from "../../types/message"
import { useUserStore } from "../../store/user.store"

interface SystemMessageItemProps {
  message: ChatMessage
  onJumpToMessage: (messageId: string) => void
  onOpenPinnedMessages: () => void
}

export default function SystemMessageItem({
  message,
  onJumpToMessage,
  onOpenPinnedMessages,
}: SystemMessageItemProps) {
  const actorId = message.actorUserId
  const users = useUserStore((state) => state.users)
  const actorDisplayName = actorId
    ? users[actorId]?.displayName || users[actorId]?.username || "Someone"
    : "Someone"

  const actorNode = actorId ? (
    <Username userId={actorId} source="CHAT">
      <span className="font-semibold text-gray-800">{actorDisplayName}</span>
    </Username>
  ) : (
    <span className="font-semibold text-gray-800">{actorDisplayName}</span>
  )

  let content = <span className="text-gray-600">System event</span>

  if (message.systemEventType === "PIN") {
    content = (
      <span className="text-gray-600">
        {actorNode} pinned{" "}
        {message.targetMessageId ? (
          <button
            type="button"
            onClick={() => onJumpToMessage(message.targetMessageId as string)}
            className="text-blue-600 hover:underline"
          >
            a message
          </button>
        ) : (
          <span>a message</span>
        )}
        {" "}in this room. {" "}
        <button
          type="button"
          onClick={onOpenPinnedMessages}
          className="text-blue-600 hover:underline"
        >
          See all pinned messages
        </button>
        .
      </span>
    )
  } else if (message.systemEventType === "JOIN") {
    content = (
      <span className="text-gray-600">
        {actorNode} joined the group.
      </span>
    )
  }

  return (
    <div className="my-2 flex justify-center" data-message-id={message.messageId}>
      <div className="rounded-full bg-gray-100 px-3 py-1 text-xs italic">{content}</div>
    </div>
  )
}
