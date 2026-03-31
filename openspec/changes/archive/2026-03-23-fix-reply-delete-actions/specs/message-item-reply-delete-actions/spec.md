## ADDED Requirements

### Requirement: Reply action SHALL populate active reply context
When a user activates Reply on a message item, the system SHALL set that message as the active reply target and display reply preview in message input.

#### Scenario: Reply action from message item
- **WHEN** user clicks Reply on message X in the message item action bar
- **THEN** reply state is set to message X and message input shows reply preview for X

#### Scenario: Reply preview clears correctly
- **WHEN** user sends the reply message or explicitly cancels reply preview
- **THEN** active reply context is cleared and input returns to non-reply mode

### Requirement: Delete action SHALL trigger confirmation and remove target message
When a user activates Delete on their own message item, the system SHALL open confirmation UI for that message and remove it after confirm.

#### Scenario: Delete action opens confirmation for selected message
- **WHEN** user clicks Delete on own message Y
- **THEN** delete confirmation opens with Y as the pending deletion target

#### Scenario: Confirm delete removes message from list
- **WHEN** user confirms deletion for message Y
- **THEN** message Y is removed from message state/list and no longer rendered

#### Scenario: Cancel delete keeps message unchanged
- **WHEN** user cancels deletion dialog
- **THEN** pending delete target is cleared and original message remains visible

### Requirement: Reply and delete actions SHALL remain functional after boundary refactors
Reply/delete action behavior SHALL continue to work when list-level and item-level ownership boundaries are enforced.

#### Scenario: Boundary-preserving action wiring
- **WHEN** message list and item components are refactored for ownership separation
- **THEN** reply and delete actions still complete their end-to-end flows without requiring full-list props in item component
