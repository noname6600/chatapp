## ADDED Requirements

### Requirement: Private (1-1) chat rooms SHALL display an unread notification badge icon in the room list
Private chat rooms SHALL render the same unread count badge as group rooms, using the existing `unreadCount` field from the room store, so users can see unread message counts without opening the room.

#### Scenario: Private chat shows unread badge when unread count is non-zero
- **WHEN** a private chat room has `unreadCount > 0` in the room store
- **THEN** a numeric badge (up to "99+") is displayed next to the room entry in the sidebar room list

#### Scenario: Badge disappears when room is marked as read
- **WHEN** user opens the private chat room and the room is marked as read (unread count reset to 0)
- **THEN** the badge is removed from the room list entry

#### Scenario: Badge updates in real-time on incoming message
- **WHEN** a new message arrives for a private chat room the user is not currently viewing
- **THEN** the badge count increments immediately in the room list without requiring a refresh

#### Scenario: Private chat badge rendering matches group chat badge rendering
- **WHEN** both a group room and a private chat room have the same unread count
- **THEN** both display visually identical badge components (same styles, positioning, and overflow behavior)
