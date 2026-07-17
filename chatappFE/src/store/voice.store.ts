import { create } from "zustand"
import type { VoiceParticipant } from "../api/voice.service"
import type { RemoteTrack } from "livekit-client"

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
  isScreenSharing: boolean
  /** userId → remote screen-share track */
  screenShareByUser: Record<string, RemoteTrack>
  /** userId → derived from RoomEvent.TrackMuted/TrackUnmuted on their mic track */
  remoteMicMutedByUser: Record<string, boolean>
  /** userId → derived from RoomEvent.ParticipantAttributesChanged ("deafened" attribute) */
  remoteDeafenedByUser: Record<string, boolean>
  /** userId → this user's own slider position for that participant (0–1), survives a "mute for me" toggle */
  remoteVolumeByUser: Record<string, number>
  /** userId → "mute for me" override, independent of the slider position above */
  remoteMutedForMeByUser: Record<string, boolean>
  /** Set when a join attempt fails (e.g. mic permission denied) — cleared on the next attempt */
  joinError: string | null
  /**
   * Backend says this account is recorded as active in a voice room that
   * *this* tab isn't the live connection for — either connected on another
   * device, or a stale record left behind by a refresh. Deliberately not
   * part of reset()/initialState: it reflects state elsewhere, not this
   * tab's own connection lifecycle, so a normal leave/room-switch here
   * must not wipe it.
   */
  reconnectPrompt: { chatRoomId: string; liveElsewhere: boolean } | null

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
  setScreenSharing: (sharing: boolean) => void
  setRemoteScreenTrack: (userId: string, track: RemoteTrack) => void
  clearRemoteScreenTrack: (userId: string) => void
  setRemoteMicMuted: (userId: string, muted: boolean) => void
  setRemoteDeafened: (userId: string, deafened: boolean) => void
  setParticipantVolumePref: (userId: string, volume: number) => void
  setMutedForMe: (userId: string, muted: boolean) => void
  setJoinError: (error: string | null) => void
  setReconnectPrompt: (prompt: { chatRoomId: string; liveElsewhere: boolean } | null) => void
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
  isScreenSharing: false,
  screenShareByUser: {} as Record<string, RemoteTrack>,
  remoteMicMutedByUser: {} as Record<string, boolean>,
  remoteDeafenedByUser: {} as Record<string, boolean>,
  remoteVolumeByUser: {} as Record<string, number>,
  remoteMutedForMeByUser: {} as Record<string, boolean>,
  joinError: null as string | null,
}

export const useVoiceStore = create<VoiceState>((set) => ({
  ...initialState,
  reconnectPrompt: null,

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

  setScreenSharing: (isScreenSharing) => set({ isScreenSharing }),

  setRemoteScreenTrack: (userId, track) =>
    set((state) => ({
      screenShareByUser: { ...state.screenShareByUser, [userId]: track },
    })),

  clearRemoteScreenTrack: (userId) =>
    set((state) => {
      const next = { ...state.screenShareByUser }
      delete next[userId]
      return { screenShareByUser: next }
    }),

  setRemoteMicMuted: (userId, muted) =>
    set((state) => ({ remoteMicMutedByUser: { ...state.remoteMicMutedByUser, [userId]: muted } })),

  setRemoteDeafened: (userId, deafened) =>
    set((state) => ({ remoteDeafenedByUser: { ...state.remoteDeafenedByUser, [userId]: deafened } })),

  setParticipantVolumePref: (userId, volume) =>
    set((state) => ({ remoteVolumeByUser: { ...state.remoteVolumeByUser, [userId]: volume } })),

  setMutedForMe: (userId, muted) =>
    set((state) => ({ remoteMutedForMeByUser: { ...state.remoteMutedForMeByUser, [userId]: muted } })),

  setJoinError: (joinError) => set({ joinError }),

  setReconnectPrompt: (reconnectPrompt) => set({ reconnectPrompt }),

  // Deliberately does not touch reconnectPrompt — see its field comment above.
  reset: () =>
    set({
      ...initialState,
      speakingUserIds: new Set<string>(),
      screenShareByUser: {},
      remoteMicMutedByUser: {},
      remoteDeafenedByUser: {},
      remoteVolumeByUser: {},
      remoteMutedForMeByUser: {},
    }),
}))
