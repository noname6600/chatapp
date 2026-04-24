import { create } from "zustand"

interface UnpinStore {
  unpinningMessageId: string | null
  unpinningMessagePreview: string | null
  setUnpinning: (messageId: string, preview: string) => void
  clearUnpinning: () => void
}

const useUnpinStore = create<UnpinStore>((set) => ({
  unpinningMessageId: null,
  unpinningMessagePreview: null,
  setUnpinning: (messageId, preview) =>
    set({
      unpinningMessageId: messageId,
      unpinningMessagePreview: preview,
    }),
  clearUnpinning: () =>
    set({
      unpinningMessageId: null,
      unpinningMessagePreview: null,
    }),
}))

export function useUnpin() {
  const {
    unpinningMessageId,
    unpinningMessagePreview,
    setUnpinning,
    clearUnpinning,
  } = useUnpinStore()

  return {
    unpinningMessageId,
    unpinningMessagePreview,
    setUnpinning,
    clearUnpinning,
  }
}
