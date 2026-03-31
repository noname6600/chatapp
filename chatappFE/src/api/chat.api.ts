import { createBaseApi } from "./base.api"
import { API_URL } from "../config/api.config"

export const chatApi = createBaseApi(API_URL.CHAT)