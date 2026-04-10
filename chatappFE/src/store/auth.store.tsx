import React, { createContext, useContext, useEffect, useState } from "react"
import { connectChatSocket, disconnectChatSocket } from "../websocket/chat.socket"
import { connectPresenceSocket, disconnectPresenceSocket } from "../websocket/presence.socket"
import { getMyProfileApi } from "../api/user.service"
import { getGlobalPresenceApi, getMyPresenceApi } from "../api/presence.service"
import { usePresenceStore } from "./presence.store"

interface AuthContextType {
  accessToken: string | null
  refreshToken: string | null
  isInitializing: boolean
  login: (accessToken: string, refreshToken: string) => Promise<void>
  logout: () => void
  userId: string | null
  currentUser: any | null
  refreshCurrentUser: () => Promise<void>
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const INCOMPLETE_ACCOUNT_MESSAGE = "Account setup incomplete. Please try again in a few seconds."
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [refreshToken, setRefreshToken] = useState<string | null>(null)
  const [userId, setUserId] = useState<string | null>(null)
  const [currentUser, setCurrentUser] = useState<any>(null)
  const [isInitializing, setIsInitializing] = useState(true)

  const isIncompleteAccountError = (error: unknown): boolean => {
    const candidate = error as { code?: string; message?: string }
    if (candidate?.code === "INCOMPLETE_ACCOUNT") return true
    const message = (candidate?.message ?? "").toLowerCase()
    return message.includes("account setup incomplete")
  }

  const isProfileNotReadyError = (error: unknown): boolean => {
    const candidate = error as { code?: string; status?: number; message?: string }
    if (candidate?.code === "RESOURCE_NOT_FOUND") return true
    if (candidate?.status === 404) return true
    const message = (candidate?.message ?? "").toLowerCase()
    return message.includes("not found")
  }

  const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

  const loadSessionContextWithRetry = async () => {
    let lastError: unknown = null
    const attempts = 4

    for (let i = 0; i < attempts; i++) {
      try {
        const me = await getMyProfileApi()
        const [presence, globalPresence] = await Promise.all([
          getMyPresenceApi(),
          getGlobalPresenceApi(),
        ])

        localStorage.setItem("my_user_id", me.accountId)

        setUserId(me.accountId)
        setCurrentUser(me)
        usePresenceStore.getState().setSelfPresence(presence)
        usePresenceStore.getState().setGlobalPresence(globalPresence)
        return
      } catch (error) {
        lastError = error
        if (isProfileNotReadyError(error) && i < attempts - 1) {
          await sleep(500)
          continue
        }
        throw error
      }
    }

    throw lastError instanceof Error ? lastError : new Error("Unable to initialize session")
  }

  useEffect(() => {
    const access = localStorage.getItem("access_token")
    const refresh = localStorage.getItem("refresh_token")

    if (!access) {
      setIsInitializing(false)
      return
    }

    setAccessToken(access)
    setRefreshToken(refresh)

    ;(async () => {
      try {
        await loadSessionContextWithRetry()
      } catch {
        handleLogout(false)
      } finally {
        setIsInitializing(false)
      }
    })()
  }, [])

  useEffect(() => {
    if (!accessToken) {
      disconnectChatSocket()
      disconnectPresenceSocket()
      return
    }

    const timer = setTimeout(() => {
      connectChatSocket()
      connectPresenceSocket()
    }, 300)

    return () => {
      clearTimeout(timer)
      disconnectChatSocket()
      disconnectPresenceSocket()
    }
  }, [accessToken])

  useEffect(() => {
    const handler = () => handleLogout(false)

    window.addEventListener("auth:logout", handler)

    return () => window.removeEventListener("auth:logout", handler)
  }, [])

  useEffect(() => {
    const handler = (e: Event) => {
      const { accessToken, refreshToken } = (e as CustomEvent).detail

      setAccessToken(accessToken)
      setRefreshToken(refreshToken)
    }

    window.addEventListener("auth:refreshed", handler)

    return () => window.removeEventListener("auth:refreshed", handler)
  }, [])

  const handleLogin = async (at: string, rt: string) => {
    localStorage.setItem("access_token", at)
    localStorage.setItem("refresh_token", rt)

    setAccessToken(at)
    setRefreshToken(rt)

    try {
      await loadSessionContextWithRetry()
    } catch (error) {
      handleLogout(false)

      if (isIncompleteAccountError(error) || isProfileNotReadyError(error)) {
        throw new Error(INCOMPLETE_ACCOUNT_MESSAGE)
      }

      throw error instanceof Error ? error : new Error("Login failed")
    }
  }

  const handleLogout = (redirect = true) => {
    disconnectChatSocket()
    disconnectPresenceSocket()

    localStorage.removeItem("access_token")
    localStorage.removeItem("refresh_token")
    localStorage.removeItem("my_user_id")

    setAccessToken(null)
    setRefreshToken(null)
    setUserId(null)
    setCurrentUser(null)
    usePresenceStore.getState().setSelfPresence(null)
    usePresenceStore.getState().clearAllOnline()

    if (redirect && !window.location.pathname.includes("/login")) {
      window.location.href = "/login"
    }
  }

  const handleRefreshCurrentUser = async () => {
    try {
      const me = await getMyProfileApi()
      setCurrentUser(me)
    } catch {
      // silently ignore refresh failures
    }
  }

  if (isInitializing) return null

  return (
    <AuthContext.Provider
      value={{
        accessToken,
        refreshToken,
        isInitializing,
        login: handleLogin,
        logout: () => handleLogout(true),
        userId,
        currentUser,
        refreshCurrentUser: handleRefreshCurrentUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = (): AuthContextType => {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider")
  return ctx
}