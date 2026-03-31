import { createBaseApi } from "./base.api"
import { API_URL } from "../config/api.config"

export const uploadApi = createBaseApi(API_URL.UPLOAD)
