# reaction-toggle-without-room-id Specification

## Purpose
TBD - created by syncing change remove-reaction-toggle-room-id. Update Purpose after archive.

## Requirements
### Requirement: Reaction toggle flow does not require roomId in backend context
The system SHALL process reaction toggle requests without storing or relying on roomId in ToggleReactionContext.

#### Scenario: Context built with message-scoped identifiers only
- **WHEN** reaction toggle context is created for a request
- **THEN** context includes messageId, userId, emoji, and toggle metadata without roomId

#### Scenario: Pipeline executes successfully without context roomId
- **WHEN** reaction toggle pipeline runs for an existing message
- **THEN** all pipeline steps complete without reading roomId from context

### Requirement: Room association is derived from authoritative message data
The system SHALL derive room association from persisted message state identified by messageId whenever room-scoped actions are needed.

#### Scenario: Event publishing derives room from persisted message
- **WHEN** reaction toggle event is published
- **THEN** event routing key/channel room is resolved from the message entity linked to messageId

#### Scenario: Derived room remains consistent with stored message
- **WHEN** a valid messageId is provided for reaction toggle
- **THEN** the resolved roomId matches the message's persisted roomId

### Requirement: Reaction toggle behavior remains functionally equivalent
Removing roomId from context SHALL NOT change add/remove reaction semantics or realtime reaction notifications from the client perspective.

#### Scenario: Add reaction still works
- **WHEN** a user toggles an emoji not yet reacted by them on a message
- **THEN** reaction is added and notification/event is emitted as before

#### Scenario: Remove reaction still works
- **WHEN** a user toggles an emoji they already reacted with on a message
- **THEN** reaction is removed and notification/event is emitted as before
