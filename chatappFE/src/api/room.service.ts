import { chatApi } from "./chat.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"

import type { ApiResponse } from "../types/api"
import type { PagedBannedMembers, PagedRoomMembers, Room, RoomMember } from "../types/room"

// =========================
// CREATE ROOM
// =========================

export const createRoomApi = async (name: string): Promise<Room> => {
  try {
    const res = await chatApi.post<ApiResponse<Room>>(
      "/rooms",
      { name }
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// PRIVATE CHAT
// =========================

export const startPrivateChatApi = async (
  userId: string
): Promise<Room> => {
  try {
    const res = await chatApi.post<ApiResponse<Room>>(
      "/rooms/private-chat",
      null,
      { params: { userId } }
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// JOIN ROOM
// =========================

export const joinRoomByCodeApi = async (
  code: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      "/rooms/join-by-code",
      null,
      { params: { code } }
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const joinRoomByInviteApi = async (
  roomId: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/rooms/${roomId}/join`
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// UPDATE ROOM NAME
// =========================

export const updateRoomApi = async (
  roomId: string,
  payload: { name?: string }
): Promise<Room> => {
  try {
    const res = await chatApi.put<ApiResponse<Room>>(
      `/rooms/${roomId}/name`,
      payload
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// UPLOAD AVATAR
// =========================

export const uploadRoomAvatarApi = async (
  roomId: string,
  file: File
): Promise<string> => {
  try {
    const formData = new FormData()
    formData.append("file", file)

    const res = await chatApi.post<ApiResponse<{ url: string }>>(
      `/rooms/${roomId}/avatar`,
      formData,
      {
        headers: {
          "Content-Type": "multipart/form-data"
        }
      }
    )

    return unwrap(res).url
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// LEAVE ROOM
// =========================

export const leaveRoomApi = async (
  roomId: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/rooms/${roomId}/leave`
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const markRoomReadApi = async (
  roomId: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/rooms/${roomId}/read`
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// GET ROOM CODE
// =========================

export const getRoomCode = async (
  roomId: string
): Promise<string> => {
  try {
    const res = await chatApi.get<ApiResponse<string>>(
      `/rooms/${roomId}/code`
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// GET MY ROOMS
// =========================

export const getMyRooms = async (): Promise<Room[]> => {
  try {
    const res = await chatApi.get<ApiResponse<Room[]>>(
      "/rooms/my"
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// GET MEMBERS
// =========================

export const getRoomMembers = async (
  roomId: string
): Promise<RoomMember[]> => {
  try {
    const res = await chatApi.get<ApiResponse<RoomMember[]>>(
      `/rooms/${roomId}/members`
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getRoomMembersBulk = async (
  roomIds: string[]
): Promise<Record<string, RoomMember[]>> => {
  try {
    const res = await chatApi.post<ApiResponse<Record<string, RoomMember[]>>>(
      `/rooms/members/bulk`,
      roomIds
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const removeMemberApi = async (
  roomId: string,
  userId: string
): Promise<void> => {
  try {
    await chatApi.delete(`/rooms/${roomId}/members/${userId}`)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// MEMBER COUNT
// =========================

export const getRoomMemberCount = async (
  roomId: string
): Promise<number> => {
  try {
    const res = await chatApi.get<ApiResponse<number>>(
      `/rooms/${roomId}/member-count`
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
// =========================
// INVITE MEMBER
// =========================

export const inviteMemberApi = async (
  roomId: string,
  userId: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/rooms/${roomId}/invite`,
      null,
      { params: { userId } }
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getRoomMembersPaged = async (
  roomId: string,
  page = 0,
  size = 20,
  query?: string
): Promise<PagedRoomMembers> => {
  try {
    const res = await chatApi.get<ApiResponse<PagedRoomMembers>>(
      `/rooms/${roomId}/members/page`,
      {
        params: {
          page,
          size,
          query: query?.trim() ? query.trim() : undefined,
        },
      }
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getBannedRoomMembersPaged = async (
  roomId: string,
  page = 0,
  size = 20
): Promise<PagedBannedMembers> => {
  try {
    const res = await chatApi.get<ApiResponse<PagedBannedMembers>>(
      `/rooms/${roomId}/members/banned/page`,
      { params: { page, size } }
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const kickMemberApi = async (
  roomId: string,
  userId: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/rooms/${roomId}/members/${userId}/kick`
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const banMemberApi = async (
  roomId: string,
  userId: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/rooms/${roomId}/members/${userId}/ban`
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const unbanMemberApi = async (
  roomId: string,
  userId: string
): Promise<void> => {
  try {
    await chatApi.delete(`/rooms/${roomId}/members/${userId}/ban`)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const transferOwnershipApi = async (
  roomId: string,
  userId: string
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/rooms/${roomId}/members/${userId}/transfer-ownership`
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const bulkBanMembersApi = async (
  roomId: string,
  userIds: string[]
): Promise<void> => {
  try {
    const res = await chatApi.post<ApiResponse<void>>(
      `/rooms/${roomId}/members/ban-bulk`,
      { userIds }
    )
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}