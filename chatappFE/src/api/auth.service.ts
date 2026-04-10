import axios from "axios"
import { authApi } from "./auth.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"
import { API_URL } from "../config/api.config"
import type { ApiResponse } from "../types/api"
import type {
  ChangePasswordRequest,
  EmailVerificationStatus,
  LoginRequest,
  RegisterRequest,
  TokenPair,
} from "../types/auth"

// =========================
// Auth APIs
// =========================

export const loginApi = async (payload: LoginRequest): Promise<TokenPair> => {
  try {
    const res = await authApi.post<ApiResponse<TokenPair>>(
      "/login",
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
      "/register",
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
    const res = await axios.post<ApiResponse<TokenPair>>(`${API_URL.AUTH}/refresh`, {
      refreshToken,
    })
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const changePasswordApi = async (
  payload: ChangePasswordRequest
): Promise<void> => {
  try {
    const res = await authApi.post<ApiResponse<null>>("/password/change", payload)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const sendVerificationEmailApi = async (): Promise<void> => {
  try {
    const res = await authApi.post<ApiResponse<null>>("/email/verification/send")
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getEmailVerificationStatusApi = async (): Promise<EmailVerificationStatus> => {
  try {
    const res = await authApi.get<ApiResponse<EmailVerificationStatus>>("/email/verification/status")
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const confirmEmailVerificationApi = async (token: string): Promise<void> => {
  try {
    const res = await authApi.post<ApiResponse<null>>("/email/verification/confirm", { token })
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const forgotPasswordApi = async (email: string): Promise<void> => {
  try {
    const res = await authApi.post<ApiResponse<null>>("/password/forgot", { email })
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const resetPasswordApi = async (token: string, newPassword: string): Promise<void> => {
  try {
    const res = await authApi.post<ApiResponse<null>>("/password/reset", { token, newPassword })
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
