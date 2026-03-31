export type PresenceStatus = "ONLINE" | "AWAY" | "OFFLINE"
export type PresenceMode = "AUTO" | "MANUAL"

export interface PresenceUserState {
  userId: string
  status: PresenceStatus
}

export interface PresenceSelfState {
  mode: PresenceMode
  manualStatus: PresenceStatus | null
  effectiveStatus: PresenceStatus
  connected: boolean
}

export interface UpdatePresenceStatusPayload {
  mode: PresenceMode
  status?: PresenceStatus
}