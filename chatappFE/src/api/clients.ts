import { createBaseApi } from "./base.api";
import { API_URL } from "../config/api.config";

export const authApi = createBaseApi(API_URL.AUTH);
export const chatApi = createBaseApi(API_URL.CHAT);
export const userApi = createBaseApi(API_URL.USER);
export const uploadApi = createBaseApi(API_URL.UPLOAD);
export const friendApi = createBaseApi(API_URL.FRIEND);
