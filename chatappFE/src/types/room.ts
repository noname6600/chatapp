export type RoomType = "PRIVATE" | "GROUP"

export type RoomMemberRole = "OWNER" | "MEMBER"

export interface LastMessagePreview {
  id: string
  senderId: string
  senderName?: string  // Optional since BE does not send it; FE normalization populates it
  content: string
  createdAt: string
}

export interface Room {
  id: string
  type: RoomType

  name: string
  avatarUrl?: string | null

  createdBy: string
  createdAt: string

  myRole: RoomMemberRole

  unreadCount: number

  latestMessageAt?: string | null

  otherUserId?: string | null

  lastMessage?: LastMessagePreview | null
}

export interface RoomMember {
  userId: string
  name: string
  avatarUrl?: string | null

  role: RoomMemberRole

  joinedAt: string
}