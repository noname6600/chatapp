import { voiceApi } from "./voice.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"
import type { ApiResponse } from "../types/api"

export interface InitiateCallResponse {
  callId: string
  status: string
}

export interface AcceptCallResponse {
  token: string
  liveKitUrl: string
}

export const initiateCallApi = async (targetUserId: string): Promise<InitiateCallResponse> => {
  try {
    const res = await voiceApi.post<ApiResponse<InitiateCallResponse>>("/calls", { targetUserId })
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const acceptCallApi = async (callId: string): Promise<AcceptCallResponse> => {
  try {
    const res = await voiceApi.post<ApiResponse<AcceptCallResponse>>(`/calls/${callId}/accept`)
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const declineCallApi = async (callId: string): Promise<void> => {
  try {
    await voiceApi.post(`/calls/${callId}/decline`)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const cancelCallApi = async (callId: string): Promise<void> => {
  try {
    await voiceApi.post(`/calls/${callId}/cancel`)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const endCallApi = async (callId: string): Promise<void> => {
  try {
    await voiceApi.post(`/calls/${callId}/end`)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
