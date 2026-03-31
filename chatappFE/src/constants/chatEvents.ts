export const ChatEventType = {
  MESSAGE_SENT: "chat.message.sent",
  MESSAGE_EDITED: "chat.message.edited",
  MESSAGE_DELETED: "chat.message.deleted",
  REACTION_UPDATED: "chat.reaction.updated",

} as const

export type ChatEventTypeValue =
  (typeof ChatEventType)[keyof typeof ChatEventType]