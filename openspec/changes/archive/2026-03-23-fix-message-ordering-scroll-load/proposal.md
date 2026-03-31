## Why

Users report that message ordering is reversed (newest messages appear at the bottom instead of the top), and scrolling up to load older messages does not work. This breaks the natural chat flow and prevents discovery of conversation history.

## What Changes

- Fix message ordering so messages display in chronological order (oldest at top, newest at bottom).
- Enable scroll-to-top message loading: when user scrolls to the top of the message list, fetch and prepend older messages.
- Ensure pagination works correctly during scroll-load without losing or duplicating messages.
- Restore scroll position after prepending older messages to maintain reading context.

## Capabilities

### New Capabilities
- `message-chronological-ordering`: Ensure messages are sorted and displayed in ascending chronological order (oldest to newest, top to bottom).
- `scroll-load-older-messages`: Detect scroll-to-top and fetch older messages from the backend, prepending them to the current view.
- `pagination-state-integrity`: Maintain consistent pagination boundaries and message sequence numbers across scroll-load cycles.
- `scroll-position-restoration`: Preserve scroll position after prepending messages so user reading context is not disrupted.

### Modified Capabilities
- `message-grouping`: Update message grouping logic to work correctly with proper chronological ordering.

## Impact

Frontend:
- Update MessageList component rendering order and sort logic.
- Update scroll event handling to detect top-scroll and trigger fetch.
- Update chat store message array management during prepend operations.
- Add scroll restoration logic after async message loads.

Backend:
- No API changes expected; existing `/messages/before/{seq}` pagination contract remains valid.

User Experience:
- Chat will function as users expect: messages flow from old to new, top to bottom.
- Users can discover conversation history by scrolling up.
