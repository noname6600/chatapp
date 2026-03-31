## Why

When users return to a room or receive new messages, they have no visual indicator of how many messages they haven't read yet. This reduces awareness of conversation activity and forces users to scroll through the entire message history to find where they left off. Adding an unread message counter and a convenient jump-to-latest action improves the user experience significantly.

## What Changes

- New unread message indicator banner displayed near the top of the message list showing the count of unread messages
- New "Jump to Latest" button positioned in the indicator banner to quickly navigate to the most recent message
- The indicator is shown when entering or rejoining a room and updates as messages are read
- Banner includes contextual information (e.g., "5 unread messages" with a prominent jump button)

## Capabilities

### New Capabilities
- `message-unread-indicator`: Visual banner showing unread message count near the top of the message area; displayed when joined room has new messages since last read position
- `jump-to-latest-action`: Button action to scroll to the most recent message in the room

### Modified Capabilities
- `message-sending`: Must track unread count after messages are sent to ensure indicator updates in real-time for other users

## Impact

**Frontend**:
- New React component: `UnreadMessageIndicator` in message area
- Update message list layout to position indicator below message area header
- Add scroll handler to detect when user reads all messages (auto-hide indicator)
- Message store tracks `lastReadSeq` and calculates `unreadCount`

**Backend** (already implemented):
- `POST /api/v1/rooms/{roomId}/read` endpoint exists and persists `lastReadSeq`
- Room service already computes unread count on responses

**User-facing changes**:
- New UI element in message area (no breaking changes)
- Improved navigation workflow for catching up on messages
