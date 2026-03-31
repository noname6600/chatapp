/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import type { ChatMessage } from "../../types/message";
import MessageItem from "./MessageItem";

const mocks = vi.hoisted(() => ({
  editMessageApi: vi.fn(),
  getRoomMembers: vi.fn(),
  upsertMessage: vi.fn(),
  loadMessagesAround: vi.fn(),
  retryMessage: vi.fn(),
  toggleReaction: vi.fn(),
  setReply: vi.fn(),
  setDeleting: vi.fn(),
  mentionHandleKeyDown: vi.fn(() => null),
  mentionSelect: vi.fn(),
  mentionDetect: vi.fn(),
  mentionSetOpen: vi.fn(),
  mentionSuggestions: [] as Array<{ userId: string; displayName: string; username: string }>,
  mentionOpen: false,
  mentionSelectedIndex: 0,
}));

vi.mock("../../api/chat.service", () => ({
  editMessageApi: mocks.editMessageApi,
}));

vi.mock("../../api/room.service", () => ({
  getRoomMembers: mocks.getRoomMembers,
}));

vi.mock("../../store/chat.store", () => ({
  useChat: () => ({
    currentUserId: "user-1",
    upsertMessage: mocks.upsertMessage,
    loadMessagesAround: mocks.loadMessagesAround,
    retryMessage: mocks.retryMessage,
  }),
}));

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: { users: Record<string, unknown> }) => unknown) =>
    selector({
      users: {
        "user-1": {
          accountId: "user-1",
          username: "user1",
          displayName: "User One",
          avatarUrl: null,
        },
      },
      fetchUsers: vi.fn(),
    }),
}));

vi.mock("../../hooks/useReaction", () => ({
  useReaction: () => ({
    toggleReaction: mocks.toggleReaction,
    loading: false,
  }),
}));

vi.mock("../../hooks/useReply", () => ({
  useReply: () => ({
    setReply: mocks.setReply,
  }),
}));

vi.mock("../../hooks/useDelete", () => ({
  useDelete: () => ({
    setDeleting: mocks.setDeleting,
  }),
}));

vi.mock("../user/UserAvatar", () => ({
  default: () => <span data-testid="avatar" />,
}));

vi.mock("../user/Username", () => ({
  default: ({ children }: { children: ReactNode }) => <span>{children}</span>,
}));

vi.mock("./AttachmentDisplay", () => ({
  default: () => null,
}));

vi.mock("./MessageBlocks", () => ({
  default: () => <div data-testid="message-blocks" />,
  getRenderableBlocks: (blocks: Array<{ type: string; text?: string; attachment?: unknown }> = []) =>
    blocks.filter((block) =>
      block.type === "TEXT" ? Boolean(block.text?.trim()) : Boolean(block.attachment)
    ),
}));

vi.mock("./ReactionGroup", () => ({
  default: () => null,
}));

vi.mock("./EmojiPicker", () => ({
  default: () => null,
}));

vi.mock("./MentionAutocomplete", () => ({
  default: ({
    suggestions,
    isOpen,
    onSelect,
  }: {
    suggestions: Array<{ userId: string; displayName: string; username: string }>;
    isOpen: boolean;
    onSelect: (suggestion: { userId: string; displayName: string; username: string }) => void;
  }) =>
    isOpen && suggestions.length > 0 ? (
      <button type="button" data-testid="mention-option" onClick={() => onSelect(suggestions[0])}>
        {suggestions[0].displayName}
      </button>
    ) : null,
}));

vi.mock("../../hooks/useMention", () => ({
  useMention: () => ({
    isOpen: mocks.mentionOpen,
    query: "",
    suggestions: mocks.mentionSuggestions,
    selectedIndex: mocks.mentionSelectedIndex,
    cursorPosition: null,
    detectMention: mocks.mentionDetect,
    selectMention: mocks.mentionSelect,
    handleKeyDown: mocks.mentionHandleKeyDown,
    setCursorPosition: vi.fn(),
    setIsOpen: mocks.mentionSetOpen,
  }),
}));

vi.mock("./mention.helpers", () => ({
  buildMentionToken: (s: { username?: string; displayName?: string; userId: string }) =>
    s.username || s.displayName || s.userId,
  filterMentionSuggestions: () => [],
  extractMentionedUserIds: () => [],
}));

function makeMessage(overrides?: Partial<ChatMessage>): ChatMessage {
  return {
    messageId: "msg-1",
    roomId: "room-1",
    senderId: "user-1",
    seq: 1,
    type: "TEXT",
    content: "before edit",
    replyToMessageId: null,
    clientMessageId: null,
    createdAt: "2026-03-27T08:00:00.000Z",
    editedAt: null,
    deleted: false,
    attachments: [],
    blocks: [],
    reactions: [],
    ...overrides,
  };
}

describe("MessageItem inline edit flow", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getRoomMembers.mockResolvedValue([]);
    mocks.mentionOpen = false;
    mocks.mentionSuggestions = [];
    mocks.mentionSelectedIndex = 0;
    mocks.mentionHandleKeyDown.mockReturnValue(null);
  });

  it("applies optimistic edit and then commits server response", async () => {
    mocks.editMessageApi.mockResolvedValue(
      makeMessage({
        content: "after edit",
        editedAt: "2026-03-27T08:10:00.000Z",
      })
    );

    render(<MessageItem message={makeMessage()} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));

    const input = screen.getByDisplayValue("before edit") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "after edit" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(mocks.upsertMessage).toHaveBeenCalled();
    });

    const firstCallPayload = mocks.upsertMessage.mock.calls[0][0] as ChatMessage;
    expect(firstCallPayload.content).toBe("after edit");
    expect(firstCallPayload.editedAt).toBeTruthy();

    await waitFor(() => {
      const committedPayload = mocks.upsertMessage.mock.calls.at(-1)?.[0] as ChatMessage;
      expect(committedPayload.content).toBe("after edit");
      expect(committedPayload.editedAt).toBe("2026-03-27T08:10:00.000Z");
    });

    // loadMessagesAround is intentionally NOT called after edit to avoid scroll jumps
    expect(mocks.loadMessagesAround).not.toHaveBeenCalled();
  });

  it("rolls back optimistic edit and shows error when API fails", async () => {
    mocks.editMessageApi.mockRejectedValue(new Error("Edit request failed"));

    render(<MessageItem message={makeMessage()} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));

    const input = screen.getByDisplayValue("before edit") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "bad edit" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(mocks.editMessageApi).toHaveBeenCalledWith("msg-1", "bad edit");
    });

    await waitFor(() => {
      const rollbackPayload = mocks.upsertMessage.mock.calls.at(-1)?.[0] as ChatMessage;
      expect(rollbackPayload.content).toBe("before edit");
    });

    expect(screen.getByText(/Edit request failed/i)).toBeTruthy();
    expect(screen.getByDisplayValue("before edit")).toBeTruthy();
  });

  it("shows edit action only for current user's messages", () => {
    const { rerender } = render(
      <MessageItem message={makeMessage({ senderId: "user-1" })} />
    );

    expect(screen.getByTitle(/Edit message/i)).toBeTruthy();

    rerender(<MessageItem message={makeMessage({ senderId: "user-2" })} />);

    expect(screen.queryByTitle(/Edit message/i)).toBeNull();
  });

  it("does not show edit action for attachment-only message type", () => {
    render(
      <MessageItem
        message={makeMessage({
          type: "ATTACHMENT",
          content: "",
        })}
      />
    );

    expect(screen.queryByTitle(/Edit message/i)).toBeNull();
  });

  it("opens inline editor prefilled with current message content", () => {
    render(<MessageItem message={makeMessage({ content: "prefill me" })} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));

    expect(screen.getByDisplayValue("prefill me")).toBeTruthy();
  });

  it("cancel closes inline editor without submitting", async () => {
    render(<MessageItem message={makeMessage()} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));
    fireEvent.change(screen.getByDisplayValue("before edit"), {
      target: { value: "cancelled edit" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Cancel edit/i }));

    await waitFor(() => {
      expect(screen.queryByDisplayValue("cancelled edit")).toBeNull();
    });

    expect(mocks.editMessageApi).not.toHaveBeenCalled();
    expect(mocks.upsertMessage).not.toHaveBeenCalled();
  });

  it("renders edited timestamp indicator for edited messages", () => {
    render(
      <MessageItem
        message={makeMessage({
          content: "already edited",
          editedAt: "2026-03-27T08:11:00.000Z",
        })}
      />
    );

    expect(screen.getByText(/^edited\s/i)).toBeTruthy();
  });

  it("renders inline edited indicator for single text block", () => {
    render(
      <MessageItem
        message={makeMessage({
          type: "TEXT",
          content: "single text block",
          editedAt: "2026-03-27T08:11:00.000Z",
          blocks: [{ type: "TEXT", text: "single text block" }],
        })}
      />
    );

    const edited = screen.getByText(/^edited\s/i);
    expect(screen.queryByTestId("message-blocks")).toBeNull();
    expect(screen.getByText("single text block")).toBeTruthy();
    expect(edited.className).toContain("ml-1");
    expect(edited.className).not.toContain("mt-1");
  });

  it("uses inline editor for a single text block message", () => {
    render(
      <MessageItem
        message={makeMessage({
          type: "TEXT",
          content: "single text block",
          blocks: [{ type: "TEXT", text: "single text block" }],
        })}
      />
    );

    fireEvent.click(screen.getByTitle(/Edit message/i));

    expect(screen.getByDisplayValue("single text block")).toBeTruthy();
    expect(screen.queryByText(/Editing:\s*1 text,\s*0 media/i)).toBeNull();
  });

  it("renders newline edited indicator for multi-block message", () => {
    render(
      <MessageItem
        message={makeMessage({
          type: "MIXED",
          content: "hello image",
          editedAt: "2026-03-27T08:11:00.000Z",
          blocks: [
            { type: "TEXT", text: "hello" },
            {
              type: "ASSET",
              attachment: {
                type: "IMAGE",
                url: "https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg",
                fileName: "a.jpg",
              },
            },
          ],
        })}
      />
    );

    const edited = screen.getByText(/^edited\s/i);
    expect(screen.getByTestId("message-blocks")).toBeTruthy();
    expect(edited.className).toContain("mt-1");
  });

  it("completes full inline edit flow and displays edited content", async () => {
    const initial = makeMessage();
    const updated = makeMessage({
      content: "final edited text",
      editedAt: "2026-03-27T08:20:00.000Z",
    });

    mocks.editMessageApi.mockResolvedValue(updated);

    const { rerender } = render(<MessageItem message={initial} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));
    fireEvent.change(screen.getByDisplayValue("before edit"), {
      target: { value: "final edited text" },
    });
    fireEvent.keyDown(screen.getByDisplayValue("final edited text"), { key: "Enter" });

    await waitFor(() => {
      expect(mocks.editMessageApi).toHaveBeenCalledWith("msg-1", "final edited text");
    });

    rerender(<MessageItem message={updated} />);

    await waitFor(() => {
      expect(screen.getByText("final edited text")).toBeTruthy();
      expect(screen.getByText(/^edited\s/i)).toBeTruthy();
    });
  });

  it("shows mention autocomplete while editing", () => {
    mocks.mentionOpen = true;
    mocks.mentionSuggestions = [
      { userId: "user-2", displayName: "Alice", username: "alice" },
    ];

    render(<MessageItem message={makeMessage()} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));

    expect(screen.getByTestId("mention-option")).toBeTruthy();
  });

  it("inserts selected mention token in edit text", async () => {
    mocks.mentionOpen = true;
    mocks.mentionSuggestions = [
      { userId: "user-2", displayName: "Alice", username: "alice" },
    ];
    mocks.mentionHandleKeyDown.mockReturnValue(mocks.mentionSuggestions[0]);

    render(<MessageItem message={makeMessage()} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));
    const input = screen.getByDisplayValue("before edit") as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: "hello @al" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      const textArea = screen.getByRole("textbox") as HTMLTextAreaElement;
      expect(textArea.value).toBe("hello @alice ");
    });
  });

  it("inserts selected mention token while editing a text block", async () => {
    mocks.mentionOpen = true;
    mocks.mentionSuggestions = [
      { userId: "user-2", displayName: "Alice", username: "alice" },
    ];
    mocks.mentionHandleKeyDown.mockReturnValue(mocks.mentionSuggestions[0]);

    render(
      <MessageItem
        message={makeMessage({
          type: "MIXED",
          content: "hello image",
          blocks: [
            { type: "TEXT", text: "hello" },
            {
              type: "ASSET",
              attachment: {
                type: "IMAGE",
                url: "https://example.com/a.jpg",
                fileName: "a.jpg",
              },
            },
          ],
        })}
      />
    );

    fireEvent.click(screen.getByTitle(/Edit message/i));
    fireEvent.click(screen.getByTitle(/Click to edit/i));

    const input = screen.getByPlaceholderText("Enter text...") as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: "hello @al" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect((screen.getByPlaceholderText("Enter text...") as HTMLTextAreaElement).value).toBe(
        "hello @alice "
      );
    });
  });

  it("re-enables action bar edit button after save", async () => {
    mocks.editMessageApi.mockResolvedValue(
      makeMessage({
        content: "saved edit",
        editedAt: "2026-03-27T08:10:00.000Z",
      })
    );

    render(<MessageItem message={makeMessage()} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));
    fireEvent.change(screen.getByDisplayValue("before edit"), {
      target: { value: "saved edit" },
    });
    fireEvent.keyDown(screen.getByDisplayValue("saved edit"), { key: "Enter" });

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: /Save edit/i })).toBeNull();
      expect(screen.getByTitle(/Edit message/i)).toBeTruthy();
    });
  });

  it("renders mention highlight styling after edit save", async () => {
    const updated = makeMessage({
      content: "hello @alice",
      editedAt: "2026-03-27T08:10:00.000Z",
    });
    mocks.editMessageApi.mockResolvedValue(updated);

    const { rerender } = render(<MessageItem message={makeMessage()} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));
    fireEvent.change(screen.getByDisplayValue("before edit"), {
      target: { value: "hello @alice" },
    });
    fireEvent.keyDown(screen.getByDisplayValue("hello @alice"), { key: "Enter" });

    await waitFor(() => {
      expect(mocks.editMessageApi).toHaveBeenCalledWith("msg-1", "hello @alice");
    });

    rerender(<MessageItem message={updated} />);

    await waitFor(() => {
      const mention = screen.getByText("@alice");
      expect(mention.className).toContain("bg-amber-100");
    });
  });

  it("submits multiple mentions in a single edit", async () => {
    mocks.editMessageApi.mockResolvedValue(
      makeMessage({
        content: "hi @alice and @bob",
        editedAt: "2026-03-27T08:10:00.000Z",
      })
    );

    render(<MessageItem message={makeMessage()} />);

    fireEvent.click(screen.getByTitle(/Edit message/i));
    fireEvent.change(screen.getByDisplayValue("before edit"), {
      target: { value: "hi @alice and @bob" },
    });
    fireEvent.keyDown(screen.getByDisplayValue("hi @alice and @bob"), { key: "Enter" });

    await waitFor(() => {
      expect(mocks.editMessageApi).toHaveBeenCalledWith(
        "msg-1",
        "hi @alice and @bob"
      );
    });
  });
});
