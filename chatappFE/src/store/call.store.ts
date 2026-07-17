import { create } from "zustand"

export interface IncomingCall {
  callId: string
  callerId: string
  callerName: string | null
  callerAvatarUrl: string | null
}

export interface OutgoingCall {
  callId: string
  calleeId: string
  calleeName: string | null
  calleeAvatarUrl: string | null
}

export interface ActiveCall {
  callId: string
  otherUserId: string
  otherUsername: string | null
  otherAvatarUrl: string | null
  token: string
  liveKitUrl: string
  startedAt: number
}

interface CallState {
  incoming: IncomingCall | null
  outgoing: OutgoingCall | null
  active: ActiveCall | null
  isMuted: boolean
  setIncoming: (call: IncomingCall | null) => void
  setOutgoing: (call: OutgoingCall | null) => void
  setActive: (call: ActiveCall | null) => void
  setMuted: (muted: boolean) => void
  reset: () => void
}

export const useCallStore = create<CallState>((set) => ({
  incoming: null,
  outgoing: null,
  active: null,
  isMuted: false,

  setIncoming: (incoming) => set({ incoming }),
  setOutgoing: (outgoing) => set({ outgoing }),
  setActive: (active) => set({ active }),
  setMuted: (isMuted) => set({ isMuted }),
  reset: () => set({ incoming: null, outgoing: null, active: null, isMuted: false }),
}))
