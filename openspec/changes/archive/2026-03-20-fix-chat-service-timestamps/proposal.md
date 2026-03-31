## Why

Timestamp fields in chat-service are currently generated inconsistently across entities and pipeline steps. In message flows, `createdAt` is expected to exist but can be null before persistence, while some paths (e.g., reaction) set time in application code; this creates fragile behavior and confusion about whether request payloads must contain time values.

## What Changes

- Introduce a unified timestamp policy for chat message-domain entities (`ChatMessage`, `ChatAttachment`, `ChatMessageMention`, `ChatReaction`): server-generated only, never request-supplied
- Ensure timestamp initialization is guaranteed in persistence lifecycle for all four entities
- Remove direct timestamp assignment from reaction pipeline step and rely on entity lifecycle / auditing strategy consistently
- Add clear validation and tests that request DTOs do not require time fields and persisted entities always have non-null `createdAt`
- Ensure event-publish steps run after persistence and always publish persisted timestamps

## Capabilities

### New Capabilities
- `chat-service-timestamp-consistency`: Consistent server-side timestamp generation and propagation for message, attachment, mention, and reaction entities.

### Modified Capabilities
- (none)

## Impact

- chat-service domain entities:
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/domain/entity/ChatMessage.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/domain/entity/ChatAttachment.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/domain/entity/ChatMessageMention.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/domain/entity/ChatReaction.java`
- chat-service pipelines:
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/reaction/steps/PersistReactionStep.java`
  - publish steps under message/reaction pipelines to ensure persisted values are used
- tests in chat-service for persistence and event payload timestamp guarantees
