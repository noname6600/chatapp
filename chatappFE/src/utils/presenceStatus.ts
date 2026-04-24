import type { PresenceStatus } from "../types/presence"

export const PRESENCE_STATUS_ORDER: Record<PresenceStatus, number> = {
  ONLINE: 0,
  AWAY: 1,
  OFFLINE: 2,
}

export const getStatusColor = (status: PresenceStatus): string => {
  switch (status) {
    case "ONLINE":
      return "rgb(34 197 94)"
    case "AWAY":
      return "rgb(251 191 36)"
    case "OFFLINE":
    default:
      return "rgb(156 163 175)"
  }
}

export const getStatusRingClass = (status: PresenceStatus): string => {
  switch (status) {
    case "ONLINE":
      return "text-green-500"
    case "AWAY":
      return "text-amber-400"
    case "OFFLINE":
    default:
      return "text-gray-900"
  }
}

export const getStatusDotClass = (status: PresenceStatus): string => {
  switch (status) {
    case "ONLINE":
      return "bg-green-500"
    case "AWAY":
      return "bg-amber-400"
    case "OFFLINE":
    default:
      return "bg-gray-500"
  }
}
