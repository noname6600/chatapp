# optimistic-edit-correctness Specification

## Purpose
TBD - created by archiving change fix-optimistic-edit-avatar. Update Purpose after archive.
## Requirements
### Requirement: Optimistic edit updates preserve original message author metadata
The system SHALL ensure that when a message is being edited (optimistic state before server confirmation), the displayed message author information (avatar, user name, user ID) remains consistent with the original message's author, not the current editing user. Only the message content should update optimistically; author metadata must remain unchanged.

#### Scenario: Optimistic edit preserves original author avatar
- **WHEN** a user edits a message in a group chat sent by a different user
- **THEN** during the optimistic update phase, the message still displays the original author's avatar, not the editing user's avatar

#### Scenario: Optimistic edit preserves original author name
- **WHEN** a user edits a message sent by another user
- **THEN** the message header displays the original author's name throughout the edit optimistic phase

#### Scenario: Optimistic edit updates only message content
- **WHEN** a user edits a message and the optimistic update is applied
- **THEN** only the message content field is updated optimistically; author metadata (userId, userName, userAvatar) are unchanged from the original message

#### Scenario: One-on-one chat preserves author during optimistic edit
- **WHEN** a user edits their own message in a one-on-one chat
- **THEN** the message correctly attributes the message to the editing user (since they are the original author) with correct avatar and name

#### Scenario: Group chat preserves original author even if edited by different message thread participant
- **WHEN** user A receives a message from user B, but the UI incorrectly attempts to edit it (or similar state issue)
- **THEN** the message still displays as from user B with user B's avatar and name during optimistic edit phase
- **NOTE**: This scenario ensures the fix prevents wrong-user attribution even in edge cases

### Requirement: Edited flag displays with correct author context
The system SHALL display edit indicators (e.g., "edited" timestamp, edit badge) on messages during optimistic edit phase while maintaining the correct author attribution.

#### Scenario: Edit indicator appears with original author
- **WHEN** a message is being edited (optimistic state)
- **THEN** any edit indicators (e.g., "(edited)" label) display alongside the original author's name, not the editing user's name

### Requirement: Server response confirms author metadata consistency
The system SHALL ensure that when the server responds to a message edit request, the author metadata in the server response matches what was displayed during the optimistic phase. If any mismatch is detected, the UI reconciles to the server's canonical data.

#### Scenario: Server response maintains author consistency
- **WHEN** the server confirms a message edit
- **THEN** the author metadata (avatar, name, ID) in the server response matches the original message's author, confirming the optimistic state was correct

