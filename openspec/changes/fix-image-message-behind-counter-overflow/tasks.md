## 1. Chat Store Metadata Fix

- [x] 1.1 Update optimistic send handling in `chatappFE/src/store/chat.store.tsx` so pending placeholders do not advance authoritative `latestSeqByRoom` metadata.
- [x] 1.2 Preserve tail rendering for pending optimistic messages without keeping `Number.MAX_SAFE_INTEGER` in unread or behind-latest calculations after reconciliation.
- [x] 1.3 Ensure server-confirmed messages still refresh `latestSeqByRoom`, `hasNewerByRoom`, and related window metadata correctly for recipients and senders.

## 2. Indicator Safeguards

- [x] 2.1 Update `chatappFE/src/components/chat/MessageList.tsx` so `distanceToLatest` uses valid bounded sequence state and ignores malformed optimistic gaps.
- [x] 2.2 Verify self-sent image and attachment messages do not show overflow-like `messages behind latest` text while preserving normal incremental indicators for real incoming messages.

## 3. Regression Coverage

- [x] 3.1 Add or update chat store tests to cover optimistic self-sent attachment/image messages and confirmed-sequence reconciliation.
- [x] 3.2 Add or update message list tests to cover the sender-side top indicator after sending one image while away from latest context.
- [x] 3.3 Run targeted frontend tests for chat store and message list unread-indicator behavior.
