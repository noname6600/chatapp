## Why

Users need better temporal context when viewing recent chat messages. Messages from yesterday are currently displayed with a full date format (e.g., "3/19/2026"), which requires mental effort to interpret as "yesterday." Using a human-friendly "yesterday at HH:MM" format makes recent message timestamps instantly recognizable and improves the chat reading experience.

## What Changes

- Messages sent on the previous calendar day will display as "yesterday at HH:MM" instead of the full date
- All other messages (today and older than yesterday) retain their current timestamp format
- The timestamp formatting logic is centralized for consistent behavior across all chat views and message types

## Capabilities

### New Capabilities
- `relative-message-timestamps`: Enhance message timestamp display to use human-friendly relative dates (e.g., "yesterday") for recent messages, improving readability and reducing cognitive load when scanning chat history.

### Modified Capabilities
<!-- No existing capability requirements are changing -->

## Impact

- Affects frontend message timestamp formatting utility/component
- Changes timestamp display in message list views (one-on-one chats, group chats, all message contexts)
- Backend timestamping is unchanged; only frontend display logic is affected
- No API changes or breaking changes to message data model
