import { presenceApi } from "./presence.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"

import type { ApiResponse } from "../types/api"
import type {
  PresenceSelfState,
  PresenceUserState,
  UpdatePresenceStatusPayload,
} from "../types/presence"

export const getMyPresenceApi = async (): Promise<PresenceSelfState> => {
  try {
    const res = await presenceApi.get<ApiResponse<PresenceSelfState>>("/me")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const updateMyPresenceApi = async (
  payload: UpdatePresenceStatusPayload
): Promise<PresenceSelfState> => {
  try {
    const res = await presenceApi.put<ApiResponse<PresenceSelfState>>(
      "/me/status",
      payload
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getGlobalPresenceApi = async (): Promise<PresenceUserState[]> => {
  try {
    const res = await presenceApi.get<ApiResponse<PresenceUserState[]>>("/global")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getRoomPresenceApi = async (roomId: string): Promise<PresenceUserState[]> => {
  try {
    const res = await presenceApi.get<ApiResponse<PresenceUserState[]>>(
      `/room/${roomId}`
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}