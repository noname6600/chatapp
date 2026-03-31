## ADDED Requirements

### Requirement: Room typing indicators SHALL exclude the current user and summarize other typists
The system SHALL display typing indicators for other users in the current room without ever showing the signed-in user as typing in their own UI.

#### Scenario: Single remote user typing shows that user's name
- **WHEN** exactly one other user is actively typing in the open room
- **THEN** the chat UI displays that user's display name in the typing indicator

#### Scenario: Two or three remote users typing show names
- **WHEN** two or three other users are actively typing in the open room
- **THEN** the chat UI displays their names in the typing indicator summary

#### Scenario: More than three remote users typing collapses to generic summary
- **WHEN** more than three other users are actively typing in the open room
- **THEN** the chat UI displays a generic summary that multiple people are typing instead of listing every name

#### Scenario: Current user typing is filtered from local indicator
- **WHEN** the signed-in user is typing and no other user is typing in the open room
- **THEN** no typing indicator is shown in that user's own chat UI

### Requirement: Room typing indicators SHALL be rendered above the message input
The system SHALL render the room typing indicator at the bottom of the chat view above the message composer so it remains associated with the current conversation without overlapping the input.

#### Scenario: Typing indicator appears between message list and input
- **WHEN** one or more remote users are actively typing in the open room
- **THEN** the chat layout renders the typing indicator below the message list and above the message input area

### Requirement: Typing state SHALL clear when events stop or expire
The system SHALL remove room typing indicators when stop-typing is received, when a typing user sends a message, leaves the room, disconnects, or when typing state expires without refresh.

#### Scenario: Stop-typing event removes user from indicator
- **WHEN** the system receives a stop-typing event for a user in the current room
- **THEN** that user is removed from the room typing indicator state

#### Scenario: Typing state expires after silence
- **WHEN** no typing refresh or stop event is received for a typing user before the typing expiry threshold elapses
- **THEN** that user is removed from the room typing indicator state automatically