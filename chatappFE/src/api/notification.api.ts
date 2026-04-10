import { createBaseApi } from "./base.api"
import { API_GATEWAY_URL, API_URL } from "../config/api.config"

export const notificationApi = createBaseApi(API_URL.NOTIFICATION)
export const roomNotificationApi = createBaseApi(API_GATEWAY_URL)