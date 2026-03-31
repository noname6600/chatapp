## Why

Chat history loading currently depends on manual upward scrolling. In rooms where the initial message batch exactly fills the viewport, no scroll event is produced, so older history is never requested. After switching rooms and returning, pagination state can also prevent further history fetches even when users expect to continue loading older messages.

## What Changes

- Update the chat history loading behavior so older messages can be fetched even when the message list does not initially overflow.
- Ensure room transitions preserve correct pagination behavior so returning to a room still allows loading older history when available.
- Add explicit state handling and diagnostics for pagination blockers, including no-more-history and no-progress paths.
- Keep scroll-position restoration stable when prepending older messages.

## Capabilities

### New Capabilities
- `room-switch-resilient-history-pagination`: Robust room-aware history loading that supports auto-prefetch on no-overflow and preserves ability to continue pagination after room switches.

### Modified Capabilities
- `chat-scroll-container-isolation`: Extend requirements so older-message loading is not exclusively dependent on user-generated scroll events and remains functional across room changes.

## Impact

- Affected frontend code in chat message list pagination and room transition handling.
- Affected state management in the chat store for room-specific pagination flags and fetch guard behavior.
- Updated OpenSpec capability definitions and tests around pagination trigger and room-switch regression paths.
