export const ChatEventType = {
  MESSAGE_SENT: "chat.message.sent",
  MESSAGE_EDITED: "chat.message.edited",
  MESSAGE_DELETED: "chat.message.deleted",
  MESSAGE_PINNED: "chat.message.pinned",
  MESSAGE_UNPINNED: "chat.message.unpinned",
  REACTION_UPDATED: "chat.reaction.updated",
  MEMBER_JOINED: "chat.room.member.joined",
  MEMBER_LEFT: "chat.room.member.left",
  MEMBER_REMOVED: "chat.room.member.removed",
} as const

export type ChatEventTypeValue =
  (typeof ChatEventType)[keyof typeof ChatEventType]