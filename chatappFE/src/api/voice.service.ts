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

export interface ActiveVoiceRoomInfo {
  chatRoomId: string
  liveElsewhere: boolean
}

// Unlike the other calls here, a null payload is a normal "not active in any
// room" result, not an error — unwrap() would throw on that, so this checks
// the envelope directly instead.
export const getMyActiveVoiceRoomApi = async (): Promise<ActiveVoiceRoomInfo | null> => {
  try {
    const res = await voiceApi.get<ApiResponse<ActiveVoiceRoomInfo | null>>("/rooms/active")
    if (!res.data.success || res.data.error) {
      throw new Error(res.data.error?.message || "API error")
    }
    return res.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
