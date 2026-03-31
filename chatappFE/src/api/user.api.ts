import { createBaseApi } from "./base.api"
import { API_URL } from "../config/api.config"

export const userApi = createBaseApi(API_URL.USER)