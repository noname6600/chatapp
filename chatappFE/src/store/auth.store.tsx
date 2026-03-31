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
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [refreshToken, setRefreshToken] = useState<string | null>(null)
  const [userId, setUserId] = useState<string | null>(null)
  const [currentUser, setCurrentUser] = useState<any>(null)
  const [isInitializing, setIsInitializing] = useState(true)

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
        currentUser
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