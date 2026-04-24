import { create } from "zustand";

export type OverlaySource =
  | "SIDEBAR"
  | "CHAT"
  | "MENTION"
  | "FRIEND_SEARCH"
  | "UNKNOWN";

interface OverlayState {
  userId: string | null;
  rect: DOMRect | null;
  source: OverlaySource;

  open: (id: string, rect: DOMRect, source?: OverlaySource) => void;
  close: () => void;
}

export const useUserOverlay = create<OverlayState>((set) => ({
  userId: null,
  rect: null,
  source: "UNKNOWN",

  open: (id, rect, source = "UNKNOWN") =>
    set({
      userId: id,
      rect,
      source,
    }),

  close: () =>
    set({
      userId: null,
      rect: null,
      source: "UNKNOWN",
    }),
}));
