## Why

Starting a private chat fails when the chat service decodes user profile data that now includes an additional `username` JSON field. The strict DTO mapping raises a deserialization exception, so private chat creation cannot complete for friend-click entry flows.

## What Changes

- Define and enforce a tolerant profile-consumer contract for private-chat participant profile fetches in chat service.
- Ensure profile deserialization accepts additive JSON fields from upstream services without failing private chat startup.
- Add regression coverage for private chat initialization when upstream profile payloads include unknown fields.
- Keep existing required participant fields (`accountId`, `displayName`, `avatarUrl`) intact.

## Capabilities

### New Capabilities
- `private-chat-participant-profile-contract`: Private chat startup must remain functional when upstream profile responses add non-breaking fields.

### Modified Capabilities
- `user-profile-cache-resilience`: Clarify consumer behavior for additive profile payload fields used by private chat startup.

## Impact

- Affected backend service: friendship and/or chat private-chat startup path that requests participant basic profiles.
- Affected client contract: internal Feign DTO used by chat/friendship integration.
- Affected tests: integration or service-layer tests covering private chat creation from friend selection flow.
- Risk area: cross-service API compatibility for profile fields across user-service, friendship-service, and chat-service.
