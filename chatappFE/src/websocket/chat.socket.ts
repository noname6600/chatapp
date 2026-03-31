import type { ChatMessage } from "../types/message"
import { ChatEventType } from "../constants/chatEvents"
import { normalizeReactions } from "../utils/reactionState"

let socket: WebSocket | null = null
let reconnectTimeout: number | null = null
let manualClose = false

const eventHandlers = new Set<(event: ChatSocketEvent) => void>()
const openHandlers = new Set<() => void>()

const subscribedRooms = new Set<string>()

const WS_URL = "ws://localhost:8083/ws/chat"
const RECONNECT_DELAY = 3000

const logSendFlow = (event: string, payload: Record<string, unknown>) => {
  if (!import.meta.env.DEV) return
  if (import.meta.env.MODE === "test") return
  console.info("[send-flow][ws]", { event, ...payload })
}

export type MessageEditedPayload = {
  messageId: string
  roomId: string
  seq: number
  content: string | null
  editedAt: string | null
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

export type ChatSocketEvent =
  | { type: typeof ChatEventType.MESSAGE_SENT; payload: ChatMessage }
  | { type: typeof ChatEventType.MESSAGE_EDITED; payload: MessageEditedPayload }
  | { type: typeof ChatEventType.MESSAGE_DELETED; payload: MessageDeletedPayload }
  | { type: typeof ChatEventType.REACTION_UPDATED; payload: ReactionUpdatedPayload }

/* ================= CONNECT ================= */

export const connectChatSocket = () => {
  if (
    socket &&
    (socket.readyState === WebSocket.OPEN ||
      socket.readyState === WebSocket.CONNECTING)
  ) {
    return
  }

  const token = localStorage.getItem("access_token")
  if (!token) return

  manualClose = false

  socket = new WebSocket(`${WS_URL}?token=${token}`)

  logSendFlow("socket_connecting", { wsUrl: WS_URL })

  socket.onopen = () => {
    logSendFlow("socket_open", { subscribedRooms: subscribedRooms.size })

    subscribedRooms.forEach((roomId) => {
      socket?.send(JSON.stringify({ type: "JOIN", roomId }))
    })

    openHandlers.forEach((h) => h())
  }

  socket.onmessage = (event) => {
    try {
      const { type, payload } = JSON.parse(event.data)

      switch (type) {
        case ChatEventType.MESSAGE_SENT: {
          const msg = mapToChatMessage(payload)
          logSendFlow("socket_message_sent_received", {
            roomId: msg.roomId,
            messageId: msg.messageId,
            seq: msg.seq,
            clientMessageId: msg.clientMessageId ?? null,
          })
          eventHandlers.forEach((h) =>
            h({ type: ChatEventType.MESSAGE_SENT, payload: msg })
          )
          break
        }

        case ChatEventType.MESSAGE_EDITED: {
          eventHandlers.forEach((h) =>
            h({
              type: ChatEventType.MESSAGE_EDITED,
              payload: mapEditedPayload(payload),
            })
          )
          break
        }

        case ChatEventType.MESSAGE_DELETED: {
          eventHandlers.forEach((h) =>
            h({
              type: ChatEventType.MESSAGE_DELETED,
              payload: mapDeletedPayload(payload),
            })
          )
          break
        }

        case ChatEventType.REACTION_UPDATED: {
          eventHandlers.forEach((h) =>
            h({
              type: ChatEventType.REACTION_UPDATED,
              payload: mapReactionPayload(payload),
            })
          )
          break
        }

        default:
          break
      }
    } catch (e) {
      console.error("WS parse error", e)
    }
  }

  socket.onclose = () => {
    logSendFlow("socket_closed", {
      manualClose,
      willReconnect: !manualClose && Boolean(localStorage.getItem("access_token")),
    })

    socket = null

    if (!manualClose && localStorage.getItem("access_token")) {
      reconnectTimeout = window.setTimeout(connectChatSocket, RECONNECT_DELAY)
    }
  }

  socket.onerror = () => {
    socket?.close()
  }
}

/* ================= DISCONNECT ================= */

export const disconnectChatSocket = () => {
  manualClose = true

  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }

  socket?.close()
  socket = null

  subscribedRooms.clear()
}

/* ================= SUBSCRIBE ================= */

export const subscribeRoom = (roomId: string) => {
  subscribedRooms.add(roomId)

  logSendFlow("socket_subscribe_room", { roomId, readyState: socket?.readyState ?? null })

  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: "JOIN", roomId }))
  }
}

/* ================= HANDLERS ================= */

export const onChatEvent = (handler: (event: ChatSocketEvent) => void) => {
  eventHandlers.add(handler)

  return () => {
    eventHandlers.delete(handler)
  }
}

export const onSocketOpen = (handler: () => void) => {
  openHandlers.add(handler)

  return () => {
    openHandlers.delete(handler)
  }
}

/* ================= MAPPER ================= */

function mapToChatMessage(p: any): ChatMessage {
  return {
    messageId: p.messageId,
    roomId: p.roomId,
    senderId: p.senderId,
    seq: p.seq,
    type: p.type,
    content: p.content,
    replyToMessageId: p.replyToMessageId ?? null,
    clientMessageId: p.clientMessageId ?? null,
    createdAt: p.createdAt,
    editedAt: p.editedAt ?? null,
    deleted: p.deleted ?? false,
    attachments: p.attachments ?? [],
    blocks: p.blocks ?? [],
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