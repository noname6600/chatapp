## MODIFIED Requirements

### Requirement: Jump to latest message button
The unread/top indicator SHALL provide a prominent "Jump to Latest" action that scrolls to the most recent message and clears distance-to-latest state.

#### Scenario: Button scrolls to last message
- **WHEN** user clicks the "Jump to Latest" action
- **THEN** the message list smoothly scrolls to the most recent message in the conversation

#### Scenario: Button targets current last message during stream
- **WHEN** new messages are being streamed and user clicks "Jump to Latest"
- **THEN** the action targets the message that is currently latest at click time

#### Scenario: Distance-aware affordance appears when far from latest
- **WHEN** user is significantly above newest messages (for example, around 100-message gap)
- **THEN** top newest-jump affordance remains visible until user returns near live tail

### Requirement: Jump button accessibility
The "Jump to Latest" action SHALL be keyboard and screen reader accessible.

#### Scenario: Button is keyboard accessible
- **WHEN** user navigates to the jump action using Tab key
- **THEN** the action receives focus and can be activated via Enter or Space key

#### Scenario: Button has descriptive label for screen readers
- **WHEN** screen reader user encounters the jump action
- **THEN** it announces a descriptive latest-navigation label
