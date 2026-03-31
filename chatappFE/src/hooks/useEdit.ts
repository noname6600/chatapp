import { create } from "zustand";
import type { ChatMessage } from "../types/message";

interface EditStore {
  editingMessage: ChatMessage | null;
  setEditingMessage: (msg: ChatMessage | null) => void;
  clearEdit: () => void;
}

const useEditStore = create<EditStore>((set) => ({
  editingMessage: null,

  setEditingMessage: (msg) => set({ editingMessage: msg }),

  clearEdit: () => set({ editingMessage: null }),
}));

export function useEdit() {
  const { editingMessage, setEditingMessage, clearEdit } = useEditStore();

  return {
    editingMessage,
    setEditingMessage,
    clearEdit,
  };
}
