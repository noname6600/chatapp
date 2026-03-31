import { createBaseApi } from "./base.api"
import { API_URL } from "../config/api.config"

export const friendApi = createBaseApi(API_URL.FRIEND)
