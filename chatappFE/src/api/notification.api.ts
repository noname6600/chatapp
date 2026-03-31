import { createBaseApi } from "./base.api"
import { API_URL } from "../config/api.config"

export const notificationApi = createBaseApi(API_URL.NOTIFICATION)