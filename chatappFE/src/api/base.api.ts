import axios from "axios"
import type { AxiosInstance } from "axios"
import createAuthRefreshInterceptor from "axios-auth-refresh"
import { refreshTokenApi } from "./auth.service"

export const createBaseApi = (baseURL: string): AxiosInstance => {
  const api = axios.create({
    baseURL,
    timeout: 10000
  })

  // refresh logic
  const refreshAuthLogic = async (failedRequest: any) => {
    const refreshToken = localStorage.getItem("refresh_token")

    if (!refreshToken) {
      return Promise.reject("No refresh token")
    }

    const tokens = await refreshTokenApi(refreshToken)

    localStorage.setItem("access_token", tokens.accessToken)
    localStorage.setItem("refresh_token", tokens.refreshToken)

    failedRequest.response.config.headers["Authorization"] =
      "Bearer " + tokens.accessToken
  }

  // refresh interceptor
  createAuthRefreshInterceptor(api, refreshAuthLogic)

  // request interceptor
  api.interceptors.request.use((config) => {
    const token = localStorage.getItem("access_token")

    if (token) {
      config.headers = config.headers || {}
      config.headers["Authorization"] = `Bearer ${token}`
    }

    return config
  })

  return api
}