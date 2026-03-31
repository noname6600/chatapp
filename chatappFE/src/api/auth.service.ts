import axios from "axios"
import { authApi } from "./auth.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"
import { API_URL } from "../config/api.config"
import type { ApiResponse } from "../types/api"
import type { LoginRequest, RegisterRequest, TokenPair } from "../types/auth"

// =========================
// Auth APIs
// =========================

export const loginApi = async (payload: LoginRequest): Promise<TokenPair> => {
  try {
    const res = await authApi.post<ApiResponse<TokenPair>>(
      "/auth/login",
      payload
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const registerApi = async (
  payload: RegisterRequest
): Promise<TokenPair> => {
  try {
    const res = await authApi.post<ApiResponse<TokenPair>>(
      "/auth/register",
      payload
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const refreshTokenApi = async (
  refreshToken: string
): Promise<TokenPair> => {
  try {
    const res = await axios.post<ApiResponse<TokenPair>>(
      `${API_URL.AUTH}/auth/refresh`,
      { refreshToken }
    )
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
