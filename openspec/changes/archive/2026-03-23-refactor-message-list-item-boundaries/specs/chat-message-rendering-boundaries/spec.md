## ADDED Requirements

### Requirement: MessageList SHALL own list-level orchestration
`MessageList` SHALL own room and list orchestration concerns, including room initialization, pagination triggers, scroll lifecycle management, message grouping derivation, and list-scoped derivations such as reply-target resolution.

#### Scenario: Room data lifecycle is handled in list layer
- **WHEN** a room is opened and message list is prepared for rendering
- **THEN** `MessageList` initializes room data and list-level state without delegating lifecycle orchestration to `MessageItem`

#### Scenario: Reply targets are derived before item render
- **WHEN** a message references another message
- **THEN** reply-target lookup is resolved in list layer and item receives the resolved reply data as item-scoped input

### Requirement: MessageItem SHALL focus on single-message rendering and local interaction
`MessageItem` SHALL render one message and handle local UI interaction state (such as hover/edit mode/actions) without owning pagination, grouping, or room-level logic.

#### Scenario: Item component receives only single-item concerns
- **WHEN** rendering an item in a room
- **THEN** `MessageItem` consumes item-scoped inputs and does not require full message-list arrays or room-level pagination state

#### Scenario: Item component does not own scroll or pagination behavior
- **WHEN** user scrolls, reaches top threshold, or older messages are fetched
- **THEN** these behaviors are handled outside `MessageItem`

### Requirement: List-to-item data contract SHALL avoid list-wide prop leakage
The list-to-item contract SHALL avoid passing list-wide props that are not required for single-item rendering behavior.

#### Scenario: Single item render contract
- **WHEN** `MessageList` renders `MessageItem`
- **THEN** it passes message-level props (for example `message`, grouping flags, optional resolved reply message) and excludes list-wide structures such as full message arrays
