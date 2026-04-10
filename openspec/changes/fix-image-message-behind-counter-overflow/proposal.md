## Why

When the sender uploads an image, the chat UI can show an absurd top-of-list indicator such as `9007199254740982+ messages behind latest` even though only one message was sent. The receiver correctly gets `1` unread, so the bug is local to the sender-side frontend state and makes unread navigation look broken.

## What Changes

- Fix sender-side optimistic message handling so attachment sends do not pollute behind-latest calculations with synthetic sequence values.
- Ensure the message-list top indicator derives behind-latest counts only from valid, bounded server-confirmed sequence state.
- Preserve intended receiver behavior so other participants still see a real unread increment for the new message.
- Add regression coverage for optimistic image send flows and for the top indicator text shown after self-sent attachment messages.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `message-unread-indicator`: Tighten unread-banner and behind-latest requirements so synthetic optimistic sequence values cannot produce overflow-like counts for the sender.
- `message-sending`: Clarify optimistic send behavior so temporary client-side placeholders do not masquerade as authoritative latest sequence state during attachment/image sends.
- `self-message-unread-exclusion`: Ensure self-sent messages remain excluded from behind-latest UI math even when the optimistic placeholder is still pending.

## Impact

- Affected code: `chatappFE/src/store/chat.store.tsx`, `chatappFE/src/components/chat/MessageList.tsx`, related chat pagination or unread indicator tests.
- Affected UX: sender-side unread banner / jump-to-latest indicator after sending image or mixed-content messages.
- APIs and backend behavior: no contract change expected; receiver unread behavior should remain unchanged.
