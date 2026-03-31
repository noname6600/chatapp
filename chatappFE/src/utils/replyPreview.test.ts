import { describe, expect, it } from "vitest";
import type { ChatMessage } from "../types/message";
import {
  buildReplyPreviewModel,
  MISSING_ORIGINAL_MESSAGE_TEXT,
} from "./replyPreview";

function makeMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    messageId: "message-1",
    roomId: "room-1",
    senderId: "user-1",
    seq: 1,
    type: "TEXT",
    content: "Hello world",
    replyToMessageId: null,
    clientMessageId: null,
    createdAt: new Date(Date.UTC(2026, 0, 1, 0, 0, 1)).toISOString(),
    editedAt: null,
    deleted: false,
    attachments: [],
    reactions: [],
    ...overrides,
  };
}

describe("replyPreview view-model", () => {
  it("builds text preview with sender identity", () => {
    const model = buildReplyPreviewModel({
      message: makeMessage({ content: "Preview text" }),
      senderName: "Alice",
      senderAvatar: "https://example.com/a.png",
      currentUserId: "user-9",
    });

    expect(model.kind).toBe("text");
    expect(model.senderName).toBe("Alice");
    expect(model.senderAvatar).toBe("https://example.com/a.png");
    expect(model.previewText).toBe("Preview text");
    expect(model.isOwnTarget).toBe(false);
  });

  it("builds media preview when content is empty and attachments exist", () => {
    const model = buildReplyPreviewModel({
      message: makeMessage({
        content: "",
        attachments: [{ type: "IMAGE", url: "https://example.com/img.png" }],
      }),
      senderName: "Bob",
    });

    expect(model.kind).toBe("media");
    expect(model.previewText).toBe("Image");
  });

  it("marks own-target replies for highlight styling", () => {
    const model = buildReplyPreviewModel({
      message: makeMessage({ senderId: "me" }),
      currentUserId: "me",
    });

    expect(model.isOwnTarget).toBe(true);
    expect(model.isMissingOriginal).toBe(false);
  });

  it("uses missing-original fallback text when original is unavailable", () => {
    const model = buildReplyPreviewModel({
      message: null,
      senderName: "Unknown",
      isMissingOriginal: true,
    });

    expect(model.kind).toBe("missing");
    expect(model.isMissingOriginal).toBe(true);
    expect(model.previewText).toBe(MISSING_ORIGINAL_MESSAGE_TEXT);
  });
});
