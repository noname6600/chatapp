import type { ChatMessage } from "../types/message";

const TEMP_MESSAGE_PREFIX = "temp-";

const parseTimestampMs = (value: string | null | undefined): number => {
  if (!value) return Number.MIN_SAFE_INTEGER;
  const ts = new Date(value).getTime();
  return Number.isFinite(ts) ? ts : Number.MIN_SAFE_INTEGER;
};

const getSystemEventIdentity = (message: ChatMessage): string | null => {
  if (message.type !== "SYSTEM" || !message.systemEventType) {
    return null;
  }

  if (message.systemEventType === "PIN") {
    // Pin replay duplicates can arrive with different seq values for the same
    // underlying pin event. Exclude seq from fallback identity so they collapse.
    return [
      message.roomId,
      message.systemEventType,
      message.actorUserId ?? "",
      message.targetMessageId ?? "",
      message.createdAt,
    ].join("|");
  }

  return [
    message.roomId,
    message.systemEventType,
    message.actorUserId ?? "",
    message.targetMessageId ?? "",
    message.createdAt,
  ].join("|");
};

const isOptimisticTemp = (message: ChatMessage): boolean =>
  message.messageId.startsWith(TEMP_MESSAGE_PREFIX);

const shouldReplaceExisting = (
  existing: ChatMessage,
  incoming: ChatMessage
): boolean => {
  const existingIsTemp = isOptimisticTemp(existing);
  const incomingIsTemp = isOptimisticTemp(incoming);

  if (existingIsTemp && !incomingIsTemp) return true;
  if (!existingIsTemp && incomingIsTemp) return false;

  // Same messageId updates should always refresh the stored row.
  if (existing.messageId === incoming.messageId) return true;

  // For fallback-system-identity collisions with different message IDs,
  // keep the first server-confirmed row to avoid replay-style alternation.
  return false;
};

export const compareChatMessages = (a: ChatMessage, b: ChatMessage): number => {
  if (a.seq !== b.seq) return a.seq - b.seq;

  const timeDelta = parseTimestampMs(a.createdAt) - parseTimestampMs(b.createdAt);
  if (timeDelta !== 0) return timeDelta;

  const aSystemIdentity = getSystemEventIdentity(a) ?? "";
  const bSystemIdentity = getSystemEventIdentity(b) ?? "";
  if (aSystemIdentity !== bSystemIdentity) {
    return aSystemIdentity.localeCompare(bSystemIdentity);
  }

  return a.messageId.localeCompare(b.messageId);
};

export const mergeTimelineMessages = (messages: ChatMessage[]): ChatMessage[] => {
  const byPrimaryKey = new Map<string, ChatMessage>();
  const systemIdentityToPrimary = new Map<string, string>();

  for (const incoming of messages) {
    const primaryKey = `id:${incoming.messageId}`;
    const systemIdentity = getSystemEventIdentity(incoming);

    const existingByPrimary = byPrimaryKey.get(primaryKey);
    const existingBySystemPrimary = systemIdentity
      ? systemIdentityToPrimary.get(systemIdentity)
      : undefined;
    const existingBySystem = existingBySystemPrimary
      ? byPrimaryKey.get(existingBySystemPrimary)
      : undefined;

    const existing = existingByPrimary ?? existingBySystem;

    if (!existing) {
      byPrimaryKey.set(primaryKey, incoming);
      if (systemIdentity) {
        systemIdentityToPrimary.set(systemIdentity, primaryKey);
      }
      continue;
    }

    if (!shouldReplaceExisting(existing, incoming)) {
      if (systemIdentity) {
        systemIdentityToPrimary.set(systemIdentity, `id:${existing.messageId}`);
      }
      continue;
    }

    if (existing.messageId !== incoming.messageId) {
      byPrimaryKey.delete(`id:${existing.messageId}`);
    }

    byPrimaryKey.set(primaryKey, incoming);

    if (systemIdentity) {
      systemIdentityToPrimary.set(systemIdentity, primaryKey);
    }
  }

  return Array.from(byPrimaryKey.values()).sort(compareChatMessages);
};
