# room-invite-popup-and-card-send Specification

## Purpose
Enable users to invite members to rooms via a discoverable popup that displays copyable room codes, a searchable user list, and allows sending invite card messages directly to room chat.

## Requirements

### Requirement: Room more-menu SHALL expose invite popup entrypoint
The system SHALL provide a discoverable action in the room more-menu that opens a room invite popup without leaving the current room context.

#### Scenario: Open invite popup from room more-menu
- **WHEN** a user clicks the room more-menu and selects invite members
- **THEN** a popup opens anchored to the current room context
- **AND** the chat timeline remains in place behind the popup

### Requirement: Invite popup SHALL display copyable room code
The invite popup SHALL show the room join code and provide a copy control that copies the exact code value.

#### Scenario: Copy room join code
- **WHEN** the popup is open and the user clicks copy on the room code
- **THEN** the exact room code is copied to clipboard
- **AND** the UI provides success feedback for the copy action

### Requirement: Invite popup SHALL show mention-friendly user list
The invite popup SHALL render a searchable user list where each row includes identity fields needed for mention-aware targeting, including display name, username, and about text when available.

#### Scenario: Search invite candidates by display name or username
- **WHEN** the user types into invite candidate search input
- **THEN** candidate rows are filtered using case-insensitive matching on display name and username
- **AND** each visible row shows display name and username plus about text when present

### Requirement: Invite popup SHALL send invite card to room
The popup SHALL provide an invite button per candidate that sends an invite card message into the current room with room metadata required for join from card interactions.

#### Scenario: Send invite card from candidate row
- **WHEN** the user clicks Invite on a candidate row
- **THEN** the client sends an invite-card message payload for the current room
- **AND** a new invite card message appears in the room timeline after send success
