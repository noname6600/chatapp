## ADDED Requirements

### Requirement: Composer SHALL support ordered mixed-content drafts
The system SHALL allow a user to compose one message draft as an append-only ordered sequence of text and media blocks instead of a textarea plus detached attachment tray only.

#### Scenario: User composes text-image-text-image sequence
- **WHEN** user types text, inserts an image, types more text, and inserts another image before sending
- **THEN** draft state preserves the authored order as `text [image] text [image]`

#### Scenario: Draft preview mirrors authored order
- **WHEN** draft contains interleaved text and media blocks
- **THEN** preview UI displays those blocks in the same order the final message will render

### Requirement: Composer SHALL accept clipboard-pasted images
The system SHALL accept image data pasted from the clipboard and insert it into the current draft without requiring manual file picker interaction.

#### Scenario: Ctrl+V inserts clipboard image into draft
- **WHEN** user presses `Ctrl+V` in the message composer and clipboard contains an image
- **THEN** image is inserted into the draft as a pending media block and upload starts through the existing upload flow

#### Scenario: Clipboard text and image are both preserved
- **WHEN** pasted clipboard content contains text and image data
- **THEN** the current trailing text remains in the draft and the image is added as a media block without losing either content type

### Requirement: Draft send SHALL be blocked by incomplete media blocks
The system SHALL not send a mixed-content draft while one or more inserted media blocks are still uploading or are in failed state.

#### Scenario: Uploading image blocks final send
- **WHEN** user tries to send a draft with a media block still uploading
- **THEN** send is prevented and UI indicates the unfinished block state

#### Scenario: Failed pasted image blocks final send
- **WHEN** one inserted image upload fails
- **THEN** send is prevented until the user retries or removes the failed block