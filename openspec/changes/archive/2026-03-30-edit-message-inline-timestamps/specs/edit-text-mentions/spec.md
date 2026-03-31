# edit-text-mentions Specification

## Purpose
Defines mention support within edited message text blocks, ensuring @mentions in edited text resolve users and render identically to mentions in normally-composed messages.

## ADDED Requirements

### Requirement: Edited text blocks support mention detection
When a user edits message text that contains @-prefixed tokens, the system SHALL detect and resolve those tokens as mentions using the same mention resolution system as the message composer.

#### Scenario: Mention token detected during edit
- **WHEN** a user types or pastes an @-token in the edit textarea (e.g., @john)
- **THEN** the mention autocomplete suggestion list is shown with matching users

#### Scenario: Mention token resolution matches composer behavior
- **WHEN** a mention token is entered in edit mode
- **THEN** the system uses the same mention matching logic (display name + username, case-insensitive) as the normal message composer

### Requirement: Mention selection in edits inserts user-identifying tokens
Selecting a mention suggestion during edit SHALL insert a standardized mention token (e.g., `@user#<userId>`) that preserves the user identity.

#### Scenario: Select mention during edit inserts resolved token
- **WHEN** user autocompletes and selects a mention suggestion in edit mode
- **THEN** the edit text is updated with a resolved mention token that can be parsed on message save

#### Scenario: Multiple mentions can be added in one edit
- **WHEN** user adds multiple distinct @-tokens in the same edit
- **THEN** all mention tokens are resolved and preserved when the edit is saved

### Requirement: Edited mentions render with consistent styling
When an edited message is displayed after save, any mentions in the edited text SHALL render with the same styling (color, background, interactive link behavior) as mentions in originally-composed messages.

#### Scenario: Saved mention in edited message is styled identically to normal mention
- **WHEN** an edited message containing a mention is loaded and displayed
- **THEN** the mention appears with the same background color, text styling, and user link behavior as mentions in non-edited messages

#### Scenario: Mention user resolution works for edited mentions
- **WHEN** a mention is saved during an edit
- **THEN** when the message is later displayed, the mention can be clicked and shows user info just like originally-composed mentions

### Requirement: Edit textarea supports mention input without breaking layout
The edit textarea that accepts mention input SHALL maintain focus, cursor position, and text flow consistency while autocomplete suggestions are displayed.

#### Scenario: Mention autocomplete does not unfocus edit textarea
- **WHEN** mention suggestions appear during edit
- **THEN** the edit textarea maintains focus and the user can continue typing or select from suggestions without refocusing

#### Scenario: Cursor positioning after mention selection
- **WHEN** a mention is selected from the autocomplete list during edit
- **THEN** the cursor is positioned correctly after the inserted mention token, allowing the user to continue typing
