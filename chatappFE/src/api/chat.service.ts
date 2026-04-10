import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"

import type {
  ApiResponse,
} from "../types/api"

import type {
  ChatMessage,
  MessagePage,
  MessageBlock,
  SendMessagePayload,
} from "../types/message"

import { chatApi } from "./chat.api"

// =========================
// GET MESSAGES
// =========================

export const getLatestMessages = async (
  roomId: string,
  limit = 50
): Promise<MessagePage> => {
  try {
    const res = await chatApi.get<ApiResponse<MessagePage>>(
      "/messages/latest",
      { params: { roomId, limit } }
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getMessagesBefore = async (
  roomId: string,
  beforeSeq: number,
  limit = 50
): Promise<MessagePage> => {
  try {
    const res = await chatApi.get<ApiResponse<MessagePage>>(
      "/messages/before",
      { params: { roomId, beforeSeq, limit } }
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getMessageRange = async (
  roomId: string,
  startSeq: number,
  endSeq: number
): Promise<ChatMessage[]> => {
  try {
    const res = await chatApi.get<ApiResponse<ChatMessage[]>>(
      "/messages/range",
      { params: { roomId, startSeq, endSeq } }
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getMessagesAround = async (
  roomId: string,
  messageId: string,
  halfWindow = 25
): Promise<ChatMessage[]> => {
  try {
    const res = await chatApi.get<ApiResponse<ChatMessage[]>>(
      "/messages/around",
      { params: { roomId, messageId, halfWindow } }
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// SEND MESSAGE
// =========================

export const sendMessageApi = async (
  payload: SendMessagePayload
): Promise<ChatMessage> => {
  try {
    const normalizedBlocks: MessageBlock[] | undefined =
      payload.blocks && payload.blocks.length > 0
        ? payload.blocks
            .filter((block) => {
              if (block.type === "ASSET") {
                return Boolean(block.attachment)
              }
              if (block.type === "ROOM_INVITE") {
                return Boolean(block.roomInvite?.roomId)
              }
              return Boolean(block.text?.trim())
            })
            .map((block) =>
              block.type === "TEXT"
                ? {
                    type: "TEXT",
                    text: block.text ?? "",
                  }
                : block.type === "ASSET"
                ? {
                    type: "ASSET",
                    attachment: block.attachment
                      ? {
                          type: block.attachment.type,
                          url: block.attachment.url,
                          publicId: block.attachment.publicId,
                          fileName: block.attachment.fileName ?? block.attachment.name,
                          size: block.attachment.size,
                          width: block.attachment.width,
                          height: block.attachment.height,
                          duration: block.attachment.duration,
                        }
                      : undefined,
                  }
                : {
                    type: "ROOM_INVITE",
                    roomInvite: block.roomInvite
                      ? {
                          roomId: block.roomInvite.roomId,
                          roomName: block.roomInvite.roomName,
                          roomAvatarUrl: block.roomInvite.roomAvatarUrl,
                          memberCount: block.roomInvite.memberCount,
                        }
                      : undefined,
                  }
            )
        : undefined

    // ðŸ”¥ normalize payload (cá»±c quan trá»ng)
    const cleanPayload: SendMessagePayload = {
      roomId: payload.roomId,
      content: payload.content?.trim() || undefined,
      replyToMessageId: payload.replyToMessageId,
      attachments:
        payload.attachments && payload.attachments.length > 0
          ? payload.attachments.map((attachment) => ({
              type: attachment.type,
              url: attachment.url,
              publicId: attachment.publicId,
              fileName: attachment.fileName ?? attachment.name,
              size: attachment.size,
              width: attachment.width,
              height: attachment.height,
              duration: attachment.duration,
            }))
          : undefined,
      blocks: normalizedBlocks,
      clientMessageId: payload.clientMessageId,
      mentionedUserIds: payload.mentionedUserIds,
    }

    const res = await chatApi.post<ApiResponse<ChatMessage>>(
      "/messages",
      cleanPayload
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// EDIT MESSAGE
// =========================

export const editMessageApi = async (
  messageId: string,
  content: string,
  blocks?: MessageBlock[]
): Promise<ChatMessage> => {
  try {
    const payload: { content: string; blocksJson?: string } = {
      content,
    }
    
    // If blocks are provided, carefully serialize them to preserve all attachment metadata
    if (blocks && blocks.length > 0) {
      // Create block requests matching backend MessageBlockRequest structure, preserving order
      const blockRequests = blocks.map(block => {
        if (block.type === 'ASSET') {
          // ASSET blocks preserve all attachment metadata
          const astBlock: any = {
            type: 'ASSET',
          }
          if (block.attachment) {
            astBlock.attachment = {
              type: block.attachment.type,
              url: block.attachment.url,
              publicId: block.attachment.publicId,
            }
            if (block.attachment.fileName) astBlock.attachment.fileName = block.attachment.fileName
            if (block.attachment.size) astBlock.attachment.size = block.attachment.size
            if (block.attachment.width) astBlock.attachment.width = block.attachment.width
            if (block.attachment.height) astBlock.attachment.height = block.attachment.height
            if (block.attachment.duration) astBlock.attachment.duration = block.attachment.duration
            if (block.attachment.format) astBlock.attachment.format = block.attachment.format
            if (block.attachment.resourceType) astBlock.attachment.resourceType = block.attachment.resourceType
          }
          return astBlock
        } else {
          // TEXT block
          return {
            type: 'TEXT',
            text: block.text || ''
          }
        }
      })
      
      // Serialize in exact order received, no sorting
      payload.blocksJson = JSON.stringify(blockRequests)
    }
    
    const res = await chatApi.put<ApiResponse<ChatMessage>>(
      `/messages/${messageId}`,
      payload
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// DELETE MESSAGE
// =========================

export const deleteMessageApi = async (
  messageId: string,
  actorId?: string
): Promise<void> => {
  try {
    const res = await chatApi.delete<ApiResponse<void>>(
      `/messages/${messageId}`,
      {
        data: actorId ? { actorId } : {},
      }
    )

    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// REACTION
// =========================

export const toggleReactionApi = async (
  messageId: string,
  emoji: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/messages/${messageId}/reactions/${emoji}`
    )

    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
