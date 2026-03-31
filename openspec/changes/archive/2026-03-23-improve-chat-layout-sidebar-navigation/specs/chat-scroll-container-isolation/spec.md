## ADDED Requirements

### Requirement: Message list owns vertical scrolling
The chat page SHALL isolate vertical scrolling for message history to the message list container.

#### Scenario: Scroll wheel over message area
- **WHEN** user scrolls inside the message area
- **THEN** the message list container scroll position changes
- **AND** outer page shell does not own chat history scrolling

### Requirement: Upward pagination trigger uses message container scroll state
Loading older messages SHALL be triggered from the message list container reaching configured near-top conditions, not from outer layout scroll.

#### Scenario: Near-top in message container
- **WHEN** user scrolls upward and message list scroll position reaches near-top threshold
- **THEN** the client requests older message page data

### Requirement: Scroll restoration preserves reading position after prepend
When older messages are prepended, the system SHALL preserve the user’s visible reading position in the message container.

#### Scenario: Prepend older messages
- **WHEN** older messages are loaded and inserted at the start of message list
- **THEN** the container scroll offset is adjusted to keep currently viewed messages stable
