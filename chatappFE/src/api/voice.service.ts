import { voiceApi } from "./voice.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"
import type { ApiResponse } from "../types/api"

export interface VoiceParticipant {
  userId: string
  username: string
  avatarUrl: string | null
  joinedAt: number
}

export interface JoinVoiceRoomResponse {
  token: string
  liveKitUrl: string
  participants: VoiceParticipant[]
}

export const joinVoiceRoomApi = async (chatRoomId: string): Promise<JoinVoiceRoomResponse> => {
  try {
    const res = await voiceApi.post<ApiResponse<JoinVoiceRoomResponse>>(`/rooms/${chatRoomId}/join`)
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const leaveVoiceRoomApi = async (chatRoomId: string): Promise<void> => {
  try {
    await voiceApi.post(`/rooms/${chatRoomId}/leave`)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getVoiceParticipantsApi = async (chatRoomId: string): Promise<VoiceParticipant[]> => {
  try {
    const res = await voiceApi.get<ApiResponse<VoiceParticipant[]>>(`/rooms/${chatRoomId}/participants`)
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
