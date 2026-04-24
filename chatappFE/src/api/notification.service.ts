import { notificationApi, roomNotificationApi } from "./notification.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"

import type { ApiResponse } from "../types/api"
import type {
  NotificationListResponse,
  NotificationListParams,
  Notification,
  RoomNotificationMode,
  RoomSettingsResponse,
} from "../types/notification"

type NotificationWireShape = Notification & {
  read?: boolean
}

const normalizeNotification = (item: NotificationWireShape): Notification => {
  const isRead =
    typeof item.isRead === "boolean"
      ? item.isRead
      : typeof item.read === "boolean"
        ? item.read
        : true

  return {
    ...item,
    isRead,
  }
}

export const getNotificationsApi = async (
  params?: NotificationListParams
): Promise<NotificationListResponse> => {
  try {
    const res = await notificationApi.get<ApiResponse<NotificationListResponse>>(
      "",
      {
        params,
      }
    )
    const data = unwrap(res)
    return {
      ...data,
      notifications: (data.notifications ?? []).map((item) =>
        normalizeNotification(item as NotificationWireShape)
      ),
    }
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const markNotificationReadApi = async (notificationId: string): Promise<void> => {
  try {
    const res = await notificationApi.post<ApiResponse<void>>(
      `/${notificationId}/read`
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
      "/read-all"
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
    const res = await roomNotificationApi.get<ApiResponse<RoomSettingsResponse>>(
      `/notifications/rooms/${roomId}/settings`
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const markNotificationsReadByRoomApi = async (roomId: string): Promise<void> => {
  try {
    const res = await notificationApi.post<ApiResponse<void>>(
      `/mark-read-by-room/${roomId}`
    )
    if (res.data.success === false) {
      throw new Error(res.data.error?.message || "Failed to mark room notifications read")
    }
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const clearRoomNotificationsApi = async (roomId: string): Promise<void> => {
  await markNotificationsReadByRoomApi(roomId)
}

export const updateRoomNotificationModeApi = async (
  roomId: string,
  mode: RoomNotificationMode
): Promise<RoomSettingsResponse> => {
  try {
    const res = await roomNotificationApi.put<ApiResponse<RoomSettingsResponse>>(
      `/notifications/rooms/${roomId}/settings`,
      { mode }
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

// Backward-compatible wrappers for legacy callers.
export const muteRoomApi = async (roomId: string): Promise<void> => {
  await updateRoomNotificationModeApi(roomId, "NOTHING")
}

export const unmuteRoomApi = async (roomId: string): Promise<void> => {
  await updateRoomNotificationModeApi(roomId, "NO_RESTRICT")
}
