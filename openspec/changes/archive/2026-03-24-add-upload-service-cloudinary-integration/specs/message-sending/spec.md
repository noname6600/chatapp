## MODIFIED Requirements

### Requirement: Dragged files are automatically attached to message send
The system SHALL allow users to drag files/images into the message input area, and these files SHALL be uploaded using upload-service prepared Cloudinary parameters before message send payload includes normalized attachment metadata.

#### Scenario: File dropped into input is queued for upload
- **WHEN** user drops a file onto the message input area
- **THEN** file is visually indicated as pending upload (e.g., shown in attachments preview)

#### Scenario: Dropped files and typed text are sent together
- **WHEN** user drops files, types text, and presses Enter
- **THEN** single message request is sent with both text content and normalized attachment metadata

#### Scenario: Multiple dropped files create multiple attachments
- **WHEN** user drops three image files onto input
- **THEN** all three files are queued and attached to the message when sent

#### Scenario: Attachment upload uses upload-service preparation
- **WHEN** attachment upload flow starts from chat input
- **THEN** client requests signed upload preparation from upload-service for purpose `chat-attachment`
- **AND** client uploads file to Cloudinary using returned signed parameters

#### Scenario: Failed attachment upload blocks attachment submission
- **WHEN** one or more queued attachment uploads fail before send
- **THEN** failed attachments are not included in message payload
- **AND** UI surfaces upload error state to user before retry/send
