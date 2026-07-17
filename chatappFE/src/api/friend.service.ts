import { friendApi } from "./friend.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"
import type { ApiResponse } from "../types/api"

// =========================
// Các hàm POST
// =========================

export const sendFriendRequestApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/request/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const sendFriendRequestByUsernameApi = async (username: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>("/request/username", { username })
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const acceptFriendApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/accept/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const declineFriendApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/decline/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const cancelRequestApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/cancel/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const unfriendApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/unfriend/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const blockUserApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/block/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const unblockUserApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/unblock/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// Các hàm GET
// =========================

export const getRawStatusApi = async (id: string): Promise<string> => {
  try {
    const res = await friendApi.get<ApiResponse<string>>(`/status/${id}`)
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getFriendsApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}


export const getIncomingApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/requests/incoming")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getOutgoingApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/requests/outgoing")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getBlockedByMeApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/blocks/me")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getBlockedMeApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/blocks/by-others")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getUnreadFriendRequestCountApi = async (): Promise<{ unreadCount: number }> => {
  try {
    const res = await friendApi.get<ApiResponse<{ unreadCount: number }>>("/unread-count")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
