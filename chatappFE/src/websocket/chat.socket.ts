import type { ChatMessage } from "../types/message"
import type { RoomMemberJoinedPayload, RoomMemberLeftPayload } from "../types/room"
import { ChatEventType } from "../constants/chatEvents"
import { normalizeReactions } from "../utils/reactionState"
import { sendRealtimeMessage, onRealtimeEvent, onRealtimeOpen } from "./realtime.socket"

export type MessageEditedPayload = {
  messageId: string
  roomId: string
  seq: number
  content: string | null
  editedAt: number | string | null
}

export type MessageDeletedPayload = {
  messageId: string
  roomId: string
  seq: number
  deletedAt: string | null
  deletedBy: string | null
}

export type ReactionUpdatedPayload = {
  messageId: string
  roomId: string
  userId: string
  emoji: string
  action: string
  createdAt: string | null
}

export type MessagePinEventPayload = {
  eventId: string
  roomId: string
  messageId: string
  actorId: string
  occurredAt: string | null
}

export type ChatSocketEvent =
  | { type: typeof ChatEventType.MESSAGE_SENT; payload: ChatMessage }
  | { type: typeof ChatEventType.MESSAGE_EDITED; payload: MessageEditedPayload }
  | { type: typeof ChatEventType.MESSAGE_DELETED; payload: MessageDeletedPayload }
  | { type: typeof ChatEventType.MESSAGE_PINNED; payload: MessagePinEventPayload }
  | { type: typeof ChatEventType.MESSAGE_UNPINNED; payload: MessagePinEventPayload }
  | { type: typeof ChatEventType.REACTION_UPDATED; payload: ReactionUpdatedPayload }
  | { type: typeof ChatEventType.MEMBER_JOINED; payload: RoomMemberJoinedPayload }
  | { type: typeof ChatEventType.MEMBER_LEFT; payload: RoomMemberLeftPayload }
  | { type: typeof ChatEventType.MEMBER_REMOVED; payload: RoomMemberLeftPayload }

const eventHandlers = new Set<(event: ChatSocketEvent) => void>()
const openHandlers = new Set<() => void>()
const subscribedRooms = new Set<string>()

const logSendFlow = (event: string, payload: Record<string, unknown>) => {
  if (!import.meta.env.DEV) return
  if (import.meta.env.MODE === "test") return
  console.info("[send-flow][ws]", { event, ...payload })
}

// Route incoming chat messages from the unified socket
onRealtimeEvent((msg) => {
  if (!msg.type.startsWith("chat.")) return
  try {
    const { type, payload } = msg
    switch (type) {
      case ChatEventType.MESSAGE_SENT: {
        const chatMsg = mapToChatMessage(payload)
        logSendFlow("socket_message_sent_received", {
          roomId: chatMsg.roomId,
          messageId: chatMsg.messageId,
          seq: chatMsg.seq,
          clientMessageId: chatMsg.clientMessageId ?? null,
        })
        eventHandlers.forEach((h) => h({ type: ChatEventType.MESSAGE_SENT, payload: chatMsg }))
        break
      }
      case ChatEventType.MESSAGE_EDITED:
        eventHandlers.forEach((h) =>
          h({ type: ChatEventType.MESSAGE_EDITED, payload: mapEditedPayload(payload) })
        )
        break
      case ChatEventType.MESSAGE_DELETED:
        eventHandlers.forEach((h) =>
          h({ type: ChatEventType.MESSAGE_DELETED, payload: mapDeletedPayload(payload) })
        )
        break
      case ChatEventType.MESSAGE_PINNED:
        eventHandlers.forEach((h) =>
          h({ type: ChatEventType.MESSAGE_PINNED, payload: mapMessagePinPayload(payload) })
        )
        break
      case ChatEventType.MESSAGE_UNPINNED:
        eventHandlers.forEach((h) =>
          h({ type: ChatEventType.MESSAGE_UNPINNED, payload: mapMessagePinPayload(payload) })
        )
        break
      case ChatEventType.REACTION_UPDATED:
        eventHandlers.forEach((h) =>
          h({ type: ChatEventType.REACTION_UPDATED, payload: mapReactionPayload(payload) })
        )
        break
      case ChatEventType.MEMBER_JOINED:
        eventHandlers.forEach((h) =>
          h({ type: ChatEventType.MEMBER_JOINED, payload: payload as RoomMemberJoinedPayload })
        )
        break
      case ChatEventType.MEMBER_LEFT:
        eventHandlers.forEach((h) =>
          h({ type: ChatEventType.MEMBER_LEFT, payload: payload as RoomMemberLeftPayload })
        )
        break
      case ChatEventType.MEMBER_REMOVED:
        eventHandlers.forEach((h) =>
          h({ type: ChatEventType.MEMBER_REMOVED, payload: payload as RoomMemberLeftPayload })
        )
        break
      default:
        break
    }
  } catch (e) {
    console.error("WS parse error", e)
  }
})

// Re-join all subscribed rooms after socket reconnects
onRealtimeOpen(() => {
  logSendFlow("socket_open", { subscribedRooms: subscribedRooms.size })
  subscribedRooms.forEach((roomId) => {
    sendRealtimeMessage({ type: "JOIN", roomId })
  })
  openHandlers.forEach((h) => h())
})

export const resetChatState = () => {
  subscribedRooms.clear()
}

export const subscribeRoom = (roomId: string) => {
  subscribedRooms.add(roomId)
  logSendFlow("socket_subscribe_room", { roomId })
  sendRealtimeMessage({ type: "JOIN", roomId })
}

export const onChatEvent = (handler: (event: ChatSocketEvent) => void): () => void => {
  eventHandlers.add(handler)
  return () => { eventHandlers.delete(handler) }
}

export const onSocketOpen = (handler: () => void): () => void => {
  openHandlers.add(handler)
  return () => { openHandlers.delete(handler) }
}

function mapToChatMessage(p: any): ChatMessage {
  return {
    messageId: p.messageId,
    roomId: p.roomId,
    senderId: p.senderId,
    seq: p.seq,
    type: p.type,
    content: p.content,
    replyToMessageId: p.replyToMessageId ?? null,
    replyToAuthorId: p.replyToAuthorId ?? null,
    forwardedFromMessageId: p.forwardedFromMessageId ?? null,
    systemEventType: p.systemEventType ?? null,
    actorUserId: p.actorUserId ?? null,
    targetMessageId: p.targetMessageId ?? null,
    clientMessageId: p.clientMessageId ?? null,
    createdAt: p.createdAt,
    editedAt: p.editedAt ?? null,
    deleted: p.deleted ?? false,
    attachments: p.attachments ?? [],
    blocks: p.blocks ?? [],
    mentionedUserIds: p.mentionedUserIds ?? [],
    reactions: normalizeReactions(p.reactions ?? []),
  }
}

function mapEditedPayload(p: any): MessageEditedPayload {
  return {
    messageId: p.messageId,
    roomId: p.roomId,
    seq: p.seq,
    content: p.content ?? null,
    editedAt: p.editedAt ?? null,
  }
}

function mapDeletedPayload(p: any): MessageDeletedPayload {
  return {
    messageId: p.messageId,
    roomId: p.roomId,
    seq: p.seq,
    deletedAt: p.deletedAt ?? null,
    deletedBy: p.deletedBy ?? null,
  }
}

function mapReactionPayload(p: any): ReactionUpdatedPayload {
  return {
    messageId: p.messageId,
    roomId: p.roomId,
    userId: p.userId,
    emoji: p.emoji,
    action: p.action,
    createdAt: p.createdAt ?? null,
  }
}

function mapMessagePinPayload(p: any): MessagePinEventPayload {
  return {
    eventId: p.eventId,
    roomId: p.roomId,
    messageId: p.messageId,
    actorId: p.actorId,
    occurredAt: p.occurredAt ?? null,
  }
}
