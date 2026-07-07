import { create } from "zustand"
import type { VoiceParticipant } from "../api/voice.service"

export interface VoiceState {
  activeVoiceRoomId: string | null
  participants: VoiceParticipant[]
  isMuted: boolean
  isDeafened: boolean
  isConnecting: boolean
  isConnected: boolean
  speakingUserIds: Set<string>
  lkToken: string | null
  lkUrl: string | null

  setActiveRoom: (roomId: string | null) => void
  setParticipants: (participants: VoiceParticipant[]) => void
  addParticipant: (participant: VoiceParticipant) => void
  removeParticipant: (userId: string) => void
  setMuted: (muted: boolean) => void
  setDeafened: (deafened: boolean) => void
  setConnecting: (connecting: boolean) => void
  setConnected: (connected: boolean) => void
  setSpeaking: (userIds: string[]) => void
  setCredentials: (token: string, url: string) => void
  reset: () => void
}

const initialState = {
  activeVoiceRoomId: null,
  participants: [] as VoiceParticipant[],
  isMuted: false,
  isDeafened: false,
  isConnecting: false,
  isConnected: false,
  speakingUserIds: new Set<string>(),
  lkToken: null,
  lkUrl: null,
}

export const useVoiceStore = create<VoiceState>((set) => ({
  ...initialState,

  setActiveRoom: (roomId) => set({ activeVoiceRoomId: roomId }),

  setParticipants: (participants) => set({ participants }),

  addParticipant: (participant) =>
    set((state) => {
      if (state.participants.some((p) => p.userId === participant.userId)) return state
      return { participants: [...state.participants, participant] }
    }),

  removeParticipant: (userId) =>
    set((state) => ({
      participants: state.participants.filter((p) => p.userId !== userId),
    })),

  setMuted: (isMuted) => set({ isMuted }),

  setDeafened: (isDeafened) => set({ isDeafened }),

  setConnecting: (isConnecting) => set({ isConnecting }),

  setConnected: (isConnected) => set({ isConnected }),

  setSpeaking: (userIds) => set({ speakingUserIds: new Set(userIds) }),

  setCredentials: (lkToken, lkUrl) => set({ lkToken, lkUrl }),

  reset: () =>
    set({
      ...initialState,
      speakingUserIds: new Set<string>(),
    }),
}))
