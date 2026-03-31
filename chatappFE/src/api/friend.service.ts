import { friendApi } from "./friend.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"
import type { ApiResponse } from "../types/api"

// =========================
// Các hàm POST
// =========================

export const sendFriendRequestApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/friends/request/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const acceptFriendApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/friends/accept/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const declineFriendApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/friends/decline/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const cancelRequestApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/friends/cancel/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const unfriendApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/friends/unfriend/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const blockUserApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/friends/block/${id}`)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const unblockUserApi = async (id: string): Promise<void> => {
  try {
    const res = await friendApi.post<ApiResponse<null>>(`/friends/unblock/${id}`)
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
    const res = await friendApi.get<ApiResponse<string>>(`/friends/status/${id}`)
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getFriendsApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/friends")
    console.log("RES RAW:", res) // phải có status, data, config
    return unwrap(res)           // truyền nguyên AxiosResponse vào unwrap
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}


export const getIncomingApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/friends/requests/incoming")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getOutgoingApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/friends/requests/outgoing")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getBlockedByMeApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/friends/blocks/me")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getBlockedMeApi = async (): Promise<string[]> => {
  try {
    const res = await friendApi.get<ApiResponse<string[]>>("/friends/blocks/by-others")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
