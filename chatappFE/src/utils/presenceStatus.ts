import type { PresenceStatus } from "../types/presence"

export const PRESENCE_STATUS_ORDER: Record<PresenceStatus, number> = {
  ONLINE: 0,
  AWAY: 1,
  OFFLINE: 2,
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
