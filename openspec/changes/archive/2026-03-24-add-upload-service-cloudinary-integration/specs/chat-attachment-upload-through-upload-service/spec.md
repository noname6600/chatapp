## ADDED Requirements

### Requirement: Chat attachment flow SHALL use upload-service preparation contract
Chat attachment uploads SHALL be prepared through upload-service instead of direct Cloudinary signing in chat-service.

#### Scenario: Chat client requests attachment upload preparation
- **WHEN** chat UI starts attachment upload flow
- **THEN** backend upload preparation request is routed to upload-service with purpose `chat-attachment`

#### Scenario: Chat message submit references normalized asset metadata
- **WHEN** attachment upload succeeds to Cloudinary
- **THEN** chat message send payload includes normalized asset metadata from prepared upload flow

### Requirement: Chat-service SHALL validate and persist upload metadata references
Chat-service SHALL validate attachment metadata schema and persist only accepted references with message entities.

#### Scenario: Valid attachment metadata accepted
- **WHEN** message send request includes valid attachment metadata
- **THEN** chat-service persists attachment references and message send succeeds

#### Scenario: Invalid attachment metadata rejected
- **WHEN** message send request includes invalid attachment metadata
- **THEN** chat-service rejects request with validation error and message is not persisted
