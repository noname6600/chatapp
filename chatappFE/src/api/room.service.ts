import { chatApi } from "./chat.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"

import type { ApiResponse } from "../types/api"
import type { Room, RoomMember } from "../types/room"

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