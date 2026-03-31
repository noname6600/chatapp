# jump-to-latest-action Specification

## Purpose
Define unread-banner jump behavior that quickly brings users to the most recent message, with robust accessibility and clear interaction feedback.

## Requirements

### Requirement: Jump to latest message button
The unread message banner SHALL include a prominent "Jump to Latest" button that scrolls the message list to the most recent message.

#### Scenario: Button scrolls to last message
- **WHEN** user clicks the "Jump to Latest" button in the unread banner
- **THEN** the message list smoothly scrolls to the last message in the conversation

#### Scenario: Button scrolls even if last message is already visible
- **WHEN** user clicks the "Jump to Latest" button and the last message is already in the viewport
- **THEN** the scroll position does not jump; the last message remains visible

#### Scenario: Button targets current last message
- **WHEN** new messages are being streamed and user clicks "Jump to Latest"
- **THEN** the button scrolls to the message that is currently the last in the list at the time of click

### Requirement: Jump button accessibility
The "Jump to Latest" button SHALL be keyboard and screen reader accessible.

#### Scenario: Button is keyboard accessible
- **WHEN** user navigates to the "Jump to Latest" button using Tab key
- **THEN** the button receives focus and can be activated via Enter or Space key

#### Scenario: Button has descriptive label for screen readers
- **WHEN** screen reader user encounters the button
- **THEN** it announces "Jump to latest messages" or similar descriptive text

### Requirement: Jump button visual feedback
The button SHALL provide clear visual feedback when hovered, focused, or clicked.

#### Scenario: Button shows hover state
- **WHEN** user hovers over the "Jump to Latest" button
- **THEN** the button's background or border changes (e.g., darker shade) to indicate it is clickable

#### Scenario: Button shows focus state
- **WHEN** user navigates to the button via keyboard
- **THEN** a focus ring or outline appears around the button for accessibility

#### Scenario: Button shows active/pressed state
- **WHEN** user clicks the button
- **THEN** the button briefly shows a pressed state (e.g., darker background) for visual feedback
