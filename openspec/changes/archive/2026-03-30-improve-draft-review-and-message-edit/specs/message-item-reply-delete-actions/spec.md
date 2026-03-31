## ADDED Requirements

### Requirement: User can edit sent message text and content
The system SHALL allow users to modify the text content of messages they have already sent, with server-side persistence and UI updates.

#### Scenario: Edit action is available on user's own messages
- **WHEN** user views a message they sent
- **THEN** an "Edit" action button is visible on that message row (alongside Reply, Delete actions)

#### Scenario: Edit action opens message editor
- **WHEN** user clicks the "Edit" action on a sent message
- **THEN** an inline editor opens in the message row with the current message content in an editable text field

#### Scenario: Edited message content is sent to server
- **WHEN** user modifies the message text in the editor and clicks "Save"
- **THEN** the updated content is sent to the server via a message update API endpoint

#### Scenario: Edited message reflects in UI immediately
- **WHEN** user saves changes to a message
- **THEN** the message row updates to show the new content (optimistically or after server confirmation)

#### Scenario: Edit indicator shows message was modified
- **WHEN** a message has been edited
- **THEN** an "Edited" label or timestamp (e.g., "Edited at 2:45 PM") is displayed on the message to maintain conversation transparency

#### Scenario: Edit action is not available on others' messages
- **WHEN** user views a message sent by another user
- **THEN** the "Edit" action is not visible or is disabled

#### Scenario: Editor can be dismissed without saving
- **WHEN** user clicks "Cancel" in the message editor
- **THEN** the editor closes and no changes are sent to the server

### Requirement: Message edit SHALL maintain message context and metadata
The system SHALL preserve reply context, message ordering, and other message metadata during edit operations.

#### Scenario: Reply target is unchanged after edit
- **WHEN** user edits a message that is a reply to another message
- **THEN** the reply target relationship remains intact after the edit

#### Scenario: Message position in conversation is unchanged after edit
- **WHEN** user edits a message
- **THEN** the message remains in its original chronological position (does not jump to top or bottom)

#### Scenario: Edit does not affect unread state
- **WHEN** user edits their own message
- **THEN** the edit operation does not reset unread counts or notifications for other users
