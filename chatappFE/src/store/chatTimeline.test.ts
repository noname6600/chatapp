import { describe, expect, it } from "vitest";

import type { ChatMessage } from "../types/message";
import { compareChatMessages, mergeTimelineMessages } from "./chatTimeline";

const makeMessage = (overrides: Partial<ChatMessage> = {}): ChatMessage => ({
  messageId: overrides.messageId ?? "message-default",
  roomId: overrides.roomId ?? "room-1",
  senderId: overrides.senderId ?? "user-1",
  seq: overrides.seq ?? 1,
  type: overrides.type ?? "TEXT",
  content: overrides.content ?? "hello",
  replyToMessageId: overrides.replyToMessageId ?? null,
  clientMessageId: overrides.clientMessageId ?? null,
  createdAt: overrides.createdAt ?? "2026-04-23T10:00:00.000Z",
  editedAt: overrides.editedAt ?? null,
  deleted: overrides.deleted ?? false,
  attachments: overrides.attachments ?? [],
  blocks: overrides.blocks ?? [],
  reactions: overrides.reactions ?? [],
  systemEventType: overrides.systemEventType ?? null,
  actorUserId: overrides.actorUserId ?? null,
  targetMessageId: overrides.targetMessageId ?? null,
});

describe("chat timeline merge", () => {
  it("deduplicates repeated SYSTEM pin events even when messageId differs", () => {
    const first = makeMessage({
      messageId: "system-1",
      type: "SYSTEM",
      systemEventType: "PIN",
      actorUserId: "user-a",
      targetMessageId: "msg-99",
      seq: 100,
      createdAt: "2026-04-23T10:00:00.000Z",
      content: "User A pinned a message",
    });

    const replay = makeMessage({
      messageId: "system-1-replay",
      type: "SYSTEM",
      systemEventType: "PIN",
      actorUserId: "user-a",
      targetMessageId: "msg-99",
      seq: 100,
      createdAt: "2026-04-23T10:00:00.000Z",
      content: "User A pinned a message",
    });

    const merged = mergeTimelineMessages([first, replay]);

    expect(merged).toHaveLength(1);
    expect(merged[0].messageId).toBe("system-1");
  });

  it("deduplicates repeated SYSTEM pin events even when seq differs", () => {
    const first = makeMessage({
      messageId: "system-pin-1",
      type: "SYSTEM",
      systemEventType: "PIN",
      actorUserId: "user-a",
      targetMessageId: "msg-42",
      seq: 10,
      createdAt: "2026-04-23T10:00:00.000Z",
      content: "User A pinned a message",
    });

    const replayWithDifferentSeq = makeMessage({
      messageId: "system-pin-1-replay",
      type: "SYSTEM",
      systemEventType: "PIN",
      actorUserId: "user-a",
      targetMessageId: "msg-42",
      seq: 999,
      createdAt: "2026-04-23T10:00:00.000Z",
      content: "User A pinned a message",
    });

    const merged = mergeTimelineMessages([first, replayWithDifferentSeq]);
    expect(merged).toHaveLength(1);
    expect(merged[0].messageId).toBe("system-pin-1");
  });

  it("preserves chronological order for mixed user and system rows", () => {
    const system = makeMessage({
      messageId: "system-1",
      type: "SYSTEM",
      systemEventType: "PIN",
      actorUserId: "user-a",
      targetMessageId: "msg-42",
      seq: 12,
      createdAt: "2026-04-23T10:00:00.000Z",
    });

    const user = makeMessage({
      messageId: "user-1",
      type: "TEXT",
      seq: 13,
      createdAt: "2026-04-23T10:00:01.000Z",
      content: "hello",
    });

    const merged = mergeTimelineMessages([user, system]);
    expect(merged.map((m) => m.messageId)).toEqual(["system-1", "user-1"]);
  });
});

describe("chat timeline comparator", () => {
  it("is deterministic when seq and timestamps are equal", () => {
    const a = makeMessage({ messageId: "b-id", seq: 77, createdAt: "2026-04-23T10:00:00.000Z" });
    const b = makeMessage({ messageId: "a-id", seq: 77, createdAt: "2026-04-23T10:00:00.000Z" });

    const sorted = [a, b].sort(compareChatMessages);
    expect(sorted.map((m) => m.messageId)).toEqual(["a-id", "b-id"]);
  });
});
