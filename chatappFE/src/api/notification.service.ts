import { notificationApi } from "./notification.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"

import type { ApiResponse } from "../types/api"
import type {
  NotificationListResponse,
  RoomSettingsResponse,
} from "../types/notification"

export const getNotificationsApi = async (): Promise<NotificationListResponse> => {
  try {
    const res = await notificationApi.get<ApiResponse<NotificationListResponse>>(
      "/notifications"
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const markNotificationReadApi = async (notificationId: string): Promise<void> => {
  try {
    const res = await notificationApi.post<ApiResponse<void>>(
      `/notifications/${notificationId}/read`
    )
    if (res.data.success === false) {
      throw new Error(res.data.error?.message || "Failed to mark notification read")
    }
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const markAllNotificationsReadApi = async (): Promise<void> => {
  try {
    const res = await notificationApi.post<ApiResponse<void>>(
      "/notifications/read-all"
    )
    if (res.data.success === false) {
      throw new Error(res.data.error?.message || "Failed to mark all notifications read")
    }
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getRoomMuteSettingsApi = async (roomId: string): Promise<RoomSettingsResponse> => {
  try {
    const res = await notificationApi.get<ApiResponse<RoomSettingsResponse>>(
      `/rooms/${roomId}/settings`
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const muteRoomApi = async (roomId: string): Promise<void> => {
  try {
    const res = await notificationApi.post<ApiResponse<void>>(`/rooms/${roomId}/mute`)
    if (res.data.success === false) {
      throw new Error(res.data.error?.message || "Failed to mute room")
    }
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const unmuteRoomApi = async (roomId: string): Promise<void> => {
  try {
    const res = await notificationApi.delete<ApiResponse<void>>(`/rooms/${roomId}/mute`)
    if (res.data.success === false) {
      throw new Error(res.data.error?.message || "Failed to unmute room")
    }
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}