import { createBaseApi } from "./base.api"
import { API_URL } from "../config/api.config"

export const authApi = createBaseApi(API_URL.AUTH)