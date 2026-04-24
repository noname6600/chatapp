/* @vitest-environment jsdom */

import { render, fireEvent, waitFor, screen, cleanup } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import MessageList from "./MessageList";
import type { ChatMessage } from "../../types/message";

const mocks = vi.hoisted(() => ({
  loadOlderMessages: vi.fn(async () => {}),
  loadMessagesAround: vi.fn(async () => {}),
  setActiveRoom: vi.fn(async () => {}),
  removeMessage: vi.fn(),
  fetchUsers: vi.fn(async () => {}),
  clearRoomNotifications: vi.fn(async () => {}),
  deleteMessageApi: vi.fn(async () => {}),
  setReply: vi.fn(),
  setDeleting: vi.fn(),
  clearDeleting: vi.fn(),
  toggleReaction: vi.fn(),
  editMessageApi: vi.fn(),
}));

let messageState: Record<string, ChatMessage[]> = {};
let windowMetaState: Record<string, {
  oldestSeq: number | null;
  newestSeq: number | null;
  latestSeq: number | null;
  hasOlder: boolean;
  hasNewer: boolean;
}> = {};

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    messagesByRoom: messageState,
    windowMetaByRoom: windowMetaState,
    currentUserId: "me",
    upsertMessage: vi.fn(),
    loadOlderMessages: mocks.loadOlderMessages,
    loadMessagesAround: mocks.loadMessagesAround,
    loadNewerMessages: vi.fn(async () => {}),
    setActiveRoom: mocks.setActiveRoom,
    removeMessage: mocks.removeMessage,
     setMessageListContainerRef: vi.fn(),
  }),
}));

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector?: (state: {
    users: Record<string, { accountId: string; username: string; displayName: string; avatarUrl: string | null }>;
    fetchUsers: typeof mocks.fetchUsers;
  }) => unknown) => {
    const state = {
      users: {
        me: { accountId: "me", username: "me", displayName: "Me", avatarUrl: null },
        user2: { accountId: "user2", username: "user2", displayName: "User Two", avatarUrl: null },
      },
      fetchUsers: mocks.fetchUsers,
    };

    return selector ? selector(state) : state;
  },
}));

vi.mock("../../store/room.store", () => ({
  useRooms: () => ({
    roomsById: {
      "room-1": { unreadCount: 0 },
    },
    markRoomRead: vi.fn(async () => {}),
  }),
}));

vi.mock("../../store/notification.store", () => ({
  useNotifications: () => ({
    clearRoomNotifications: mocks.clearRoomNotifications,
  }),
}));

vi.mock("../../hooks/useReply", () => ({
  useReply: () => ({
    setReply: mocks.setReply,
    replyingTo: null,
    clearReply: vi.fn(),
  }),
}));

vi.mock("../../hooks/useDelete", () => ({
  useDelete: () => ({
    deletingMessageId: null,
    deletingContent: null,
    setDeleting: mocks.setDeleting,
    clearDeleting: mocks.clearDeleting,
  }),
}));

vi.mock("../../hooks/useReaction", () => ({
  useReaction: () => ({
    toggleReaction: mocks.toggleReaction,
    loading: false,
  }),
}));

vi.mock("../../api/chat.service", () => ({
  deleteMessageApi: mocks.deleteMessageApi,
  editMessageApi: mocks.editMessageApi,
}));

vi.mock("../user/UserAvatar", () => ({
  default: ({ userId }: { userId: string }) => <span data-testid={`avatar-${userId}`} />, 
}));

vi.mock("../user/Username", () => ({
  default: ({ children }: { userId: string; children: ReactNode }) => <>{children}</>,
}));

vi.mock("./AttachmentDisplay", () => ({
  default: () => null,
}));

vi.mock("./ReactionGroup", () => ({
  default: () => null,
}));

vi.mock("./EmojiPicker", () => ({
  default: () => null,
}));

vi.mock("./ConfirmDeleteDialog", () => ({
  default: () => null,
}));

function makeMessage(seq: number, overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    messageId: `msg-${seq}`,
    roomId: "room-1",
    senderId: seq === 1 ? "me" : "user2",
    seq,
    type: "TEXT",
    content: `Message ${seq}`,
    replyToMessageId: null,
    clientMessageId: null,
    createdAt: new Date(Date.UTC(2026, 0, 1, 0, 0, seq)).toISOString(),
    editedAt: null,
    deleted: false,
    attachments: [],
    reactions: [],
    ...overrides,
  };
}

describe("MessageList reply linking behavior", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();

    messageState = {
      "room-1": [
        makeMessage(1, { senderId: "me", content: "My original" }),
        makeMessage(2, { senderId: "user2", replyToMessageId: "msg-1", content: "Reply to me" }),
        makeMessage(3, { senderId: "user2", replyToMessageId: "missing-msg", content: "Reply missing" }),
      ],
    };

    windowMetaState = {
      "room-1": {
        oldestSeq: 1,
        newestSeq: 3,
        latestSeq: 3,
        hasOlder: false,
        hasNewer: false,
      },
    };
  });

  it("highlights only reply message (not original) for reply-to-current-user", async () => {
    const { container } = render(<MessageList roomId="room-1" />);

    const originalRow = container.querySelector('[data-message-id="msg-1"]');
    const replyRow = container.querySelector('[data-message-id="msg-2"]');

    // Only reply row should be highlighted
    expect(replyRow?.className).toContain("bg-amber-50");
    expect(originalRow?.className).not.toContain("bg-amber-50");
  });

  it("renders inline unloaded-original preview as clickable load action", async () => {
    const { container } = render(<MessageList roomId="room-1" />);

    const missingReplyRow = container.querySelector('[data-message-id="msg-3"]');
    expect(missingReplyRow).not.toBeNull();

    const inlinePreview = missingReplyRow?.querySelector('[data-testid="inline-reply-preview"]') as HTMLButtonElement;
    expect(inlinePreview).not.toBeNull();
    expect(inlinePreview.textContent?.toLowerCase()).toContain("load original context");
    expect(inlinePreview.disabled).toBe(false);
  });

  it("jumps to original message when inline reply preview is clicked", async () => {
    const { container } = render(<MessageList roomId="room-1" />);

    const replyRow = container.querySelector('[data-message-id="msg-2"]');
    const inlinePreview = replyRow?.querySelector('[data-testid="inline-reply-preview"]') as HTMLButtonElement;
    fireEvent.click(inlinePreview);

    await waitFor(() => {
      expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
    });

    const originalRow = container.querySelector('[data-message-id="msg-1"]');
    expect(originalRow?.className).toContain("ring-yellow-200");
  });

  it("falls back to unavailable state when target cannot be resolved", async () => {
    mocks.loadMessagesAround.mockRejectedValueOnce(new Error("not found"));

    const { container } = render(<MessageList roomId="room-1" />);
    const missingReplyRow = container.querySelector('[data-message-id="msg-3"]');
    const inlinePreview = missingReplyRow?.querySelector('[data-testid="inline-reply-preview"]') as HTMLButtonElement;

    fireEvent.click(inlinePreview);

    await waitFor(() => {
      expect(inlinePreview.textContent?.toLowerCase()).toContain("cannot load the original message");
    });

    expect(inlinePreview.disabled).toBe(true);
  });

  it("highlights full message row when current user is mentioned", () => {
    messageState = {
      "room-1": [
        makeMessage(1, {
          senderId: "user2",
          content: "Hi @me",
          mentionedUserIds: ["me"],
        }),
      ],
    };

    const { container } = render(<MessageList roomId="room-1" />);
    const row = container.querySelector('[data-message-id="msg-1"]');

    expect(row?.className).toContain("bg-amber-50");
  });

  it("highlights mention token only when current user is not mentioned", () => {
    const view = render(<MessageList roomId="room-1" />);

    messageState = {
      "room-1": [
        makeMessage(1, {
          senderId: "user2",
          content: "Hi @user2",
          mentionedUserIds: ["user2"],
        }),
      ],
    };
    windowMetaState = {
      "room-1": {
        oldestSeq: 1,
        newestSeq: 1,
        latestSeq: 1,
        hasOlder: false,
        hasNewer: false,
      },
    };

    view.rerender(<MessageList roomId="room-1" />);

    const { container } = view;
    const row = container.querySelector('[data-message-id="msg-1"]');

    expect(row?.className).not.toContain("bg-amber-50");
    expect(screen.getByText("@User Two")).toBeTruthy();
  });
});
