import { API_URL } from "../config/api.config"

export const requestRealtimeTicket = async (): Promise<string | null> => {
  const authorization = localStorage.getItem("access_token")
  if (!authorization) {
    return null
  }

  const response = await fetch(`${API_URL.REALTIME}/ticket`, {
    method: "POST",
    headers: {
      Authorization: authorization.startsWith("Bearer ")
        ? authorization
        : `Bearer ${authorization}`,
    },
  })

  if (!response.ok) {
    return null
  }

  const body = (await response.json()) as { ticket?: string }
  const ticket = body.ticket?.trim()
  return ticket ? `${ticket}` : null
}