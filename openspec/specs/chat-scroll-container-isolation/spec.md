# chat-scroll-container-isolation Specification

## Purpose
Defines scroll ownership for the chat experience so message history, pagination, and scroll restoration behave consistently within the message list container.

## Requirements

### Requirement: Message list owns vertical scrolling
The chat page SHALL isolate vertical scrolling for message history to the message list container.

#### Scenario: Scroll wheel over message area
- **WHEN** user scrolls inside the message area
- **THEN** the message list container scroll position changes
- **AND** outer page shell does not own chat history scrolling

### Requirement: Upward pagination trigger uses message container scroll state
Loading older messages SHALL be triggered from message list container conditions, with fallback behavior when container overflow is insufficient for user-generated upward scroll.

#### Scenario: Near-top in message container
- **WHEN** user scrolls upward and message list scroll position reaches near-top threshold
- **THEN** the client requests older message page data

#### Scenario: No-overflow fallback trigger
- **WHEN** message list container does not overflow after render and upward scroll cannot occur
- **THEN** the client auto-requests older message page data until scroll becomes possible or no additional progress is available

#### Scenario: Room switch return keeps trigger functional
- **WHEN** user returns to a previously visited room
- **THEN** near-top and no-overflow pagination triggers remain functional for that room

### Requirement: Scroll restoration preserves reading position after prepend
When older messages are prepended, the system SHALL preserve the user’s visible reading position in the message container.

#### Scenario: Prepend older messages
- **WHEN** older messages are loaded and inserted at the start of message list
- **THEN** the container scroll offset is adjusted to keep currently viewed messages stable