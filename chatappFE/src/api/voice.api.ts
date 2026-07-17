import { createBaseApi } from "./base.api"
import { API_URL } from "../config/api.config"

export const voiceApi = createBaseApi(API_URL.VOICE)
