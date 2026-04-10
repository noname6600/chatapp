# message-unread-indicator Specification

## ADDED Requirements

### Requirement: Exclude current user's messages from unread count
The system SHALL exclude messages sent by the current user from unread count calculations. When displaying the unread banner, the count SHALL reflect only messages from other participants, preventing false unread indicators for the user's own outgoing messages.

#### Scenario: Banner does not increment when user sends message
- **WHEN** the current user sends a message (optimistic or confirmed)
- **THEN** the unread count in the banner remains unchanged

#### Scenario: Banner increments only for messages from other users
- **WHEN** a MESSAGE_SENT event arrives via WebSocket from another user with `senderId !== currentUserId`
- **THEN** the unread count increments by 1 and banner updates immediately

#### Scenario: Unread count calculation filters self-messages on room entry
- **WHEN** user enters a room and unread count is calculated from `latestSeq - lastReadSeq`
- **THEN** the backend calculation (reflected in unreadCount returned by API) does not include messages where `senderId === currentUserId`

#### Scenario: Behind-latest indicator on unread banner excludes self messages
- **WHEN** the unread banner shows "X new messages since you were last here"
- **THEN** the count X includes only messages from other users, not from the current user
