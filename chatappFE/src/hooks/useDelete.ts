import { create } from "zustand";

interface DeleteStore {
  deletingMessageId: string | null;
  deletingContent: string | null;
  setDeleting: (messageId: string, content: string) => void;
  clearDeleting: () => void;
}

const useDeleteStore = create<DeleteStore>((set) => ({
  deletingMessageId: null,
  deletingContent: null,

  setDeleting: (messageId, content) =>
    set({ deletingMessageId: messageId, deletingContent: content }),

  clearDeleting: () =>
    set({ deletingMessageId: null, deletingContent: null }),
}));

export function useDelete() {
  const { deletingMessageId, deletingContent, setDeleting, clearDeleting } =
    useDeleteStore();

  return {
    deletingMessageId,
    deletingContent,
    setDeleting,
    clearDeleting,
  };
}
