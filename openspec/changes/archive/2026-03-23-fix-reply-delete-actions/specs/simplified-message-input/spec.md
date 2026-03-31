## MODIFIED Requirements

### Requirement: Message composer supports keyboard send and multiline behavior
The message composer SHALL continue keyboard send behavior and SHALL reflect active reply context selected from message item actions.

#### Scenario: Enter sends current draft
- **WHEN** user types a message and presses Enter
- **THEN** the composer submits the message

#### Scenario: Alt+Enter inserts newline
- **WHEN** user presses Alt+Enter in the composer
- **THEN** a newline is inserted without submitting

#### Scenario: Reply preview appears from item action
- **WHEN** user clicks Reply on a message item
- **THEN** composer displays reply preview with the selected target and remains editable

#### Scenario: Reply preview can be cleared without send
- **WHEN** user clears reply preview from composer UI
- **THEN** reply state is removed and subsequent sends are non-reply messages unless a new reply target is selected
