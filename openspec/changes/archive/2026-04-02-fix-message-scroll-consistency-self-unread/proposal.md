## Why

The chat message list has inconsistent scroll behavior when sending and receiving messages. When sending a message while viewing older messages, the view doesn't smoothly follow the new message, creating a disjointed experience. Additionally, unread counts are displayed for messages sent by the current user, which is incorrect—users should not see unread badges for their own messages.

## What Changes

- **Auto-scroll on send**: When user sends a message, the message list smoothly scrolls to the bottom to display the new message and sending status, providing immediate visual confirmation.
- **Smooth message append**: When new messages arrive (own or others), they appear at the bottom with smooth scrolling instead of jarring jumps, maintaining reading context.
- **Self-message unread suppression**: Messages sent by the current user do NOT increment the unread count or contribute to "behind-latest" displays, since the user has already seen their own message.
- **Jump consistency**: When sending while reading old messages, the jump to the new message is deterministic and smooth, not abrupt or flickering.

## Capabilities

### New Capabilities
- `message-auto-scroll-on-send`: When a message is sent, the message list automatically scrolls to display the new message at the bottom with smooth animation, providing clear visual feedback of message sending.
- `self-message-unread-exclusion`: Unread count calculations and "behind-latest" indicators exclude messages sent by the current user, preventing false unread badges when the user observes their own outgoing messages.

### Modified Capabilities
- `message-unread-indicator`: Update unread count display logic to explicitly exclude messages from the current user's `userId`. Unread badges and count should only reflect messages from other participants.

## Impact

**Affected Code:**
- Frontend: `chatappFE/src/stores/chat.store.tsx` (scroll behavior on new message events)
- Frontend: `chatappFE/src/stores/room.store.tsx` (room last-message preview and unread display)
- Frontend: `chatappFE/src/components/chat/ChatMessageList.tsx` (auto-scroll logic on message send)
- Frontend: `chatappFE/src/stores/notification.store.tsx` (unread count calculation excluding self messages)

**APIs/Services:**
- Message WebSocket events (MESSAGE_SENT, MESSAGE_EDITED) must populate `senderId` for filtering
- Room API responses must include `currentUserId` context for self-message filtering

**User Impact:**
- Better visual feedback and reduced UI flashing when sending messages
- Correct unread count that reflects actual unread messages from others
- Smoother, more predictable scrolling behavior
