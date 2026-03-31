export type MessageType =
  | "TEXT"
  | "ATTACHMENT"
  | "MIXED"

export type MessageBlockType =
  | "TEXT"
  | "ASSET"

export type AttachmentType =
  | "IMAGE"
  | "VIDEO"
  | "FILE"


export interface Reaction {
  emoji: string
  count: number
  reactedByMe?: boolean
}


export interface Attachment {
  id?: string
  type: AttachmentType
  url: string
  publicId?: string
  name?: string
  fileName?: string
  size?: number
  width?: number
  height?: number
  duration?: number
  format?: string
  resourceType?: string
}

export interface MessageBlock {
  type: MessageBlockType
  text?: string
  attachment?: Attachment
}


export interface ChatMessage {
  messageId: string
  roomId: string
  senderId: string

  seq: number
  type: MessageType

  content: string | null
  replyToMessageId: string | null

  clientMessageId?: string | null

  createdAt: string
  editedAt: string | null

  deleted: boolean

  attachments: Attachment[]
  blocks?: MessageBlock[]
  mentionedUserIds?: string[]
  reactions: Reaction[]

  // Local-only optimistic delivery state for send lifecycle UX.
  deliveryStatus?: "pending" | "sent" | "failed"
}

export interface MessagePage {
  messages: ChatMessage[]
  hasMore: boolean
}

export type SendMessagePayload = {
  roomId: string
  content?: string
  replyToMessageId?: string
  attachments?: Attachment[]
  blocks?: MessageBlock[]
  clientMessageId?: string
  mentionedUserIds?: string[]
}