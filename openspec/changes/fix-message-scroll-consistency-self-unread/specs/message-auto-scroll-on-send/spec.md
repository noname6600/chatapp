# message-auto-scroll-on-send Specification

## Purpose
Define automatic scroll behavior when messages are sent or received, ensuring the message list smoothly follows new messages when the user is at the bottom of the conversation, while respecting user intent when reading older messages.

## ADDED Requirements

### Requirement: Auto-scroll to bottom when at bottom and new message arrives
The system SHALL automatically scroll the message list to the bottom with smooth animation when a new message (sent by user or received from others) arrives and the user is currently viewing the bottom of the message list.

#### Scenario: User at bottom receives new message, scrolls to show it
- **WHEN** `scrollTop + clientHeight >= scrollHeight - 50` (user is at bottom) and a MESSAGE_SENT or MESSAGE_RECEIVED event arrives
- **THEN** message list smoothly scrolls to bottom so the new message is visible in the viewport

#### Scenario: User scrolling up (reading old) receives new message, does not auto-scroll
- **WHEN** user is scrolled above the bottom (`scrollTop + clientHeight < scrollHeight - 50`) and a new message arrives
- **THEN** the new message is added to the list but scroll position does NOT change; user remains in their reading context

#### Scenario: User sends message, auto-scrolls to show optimistic placeholder
- **WHEN** user types and sends a message while at the bottom of the list
- **THEN** before server confirmation, the optimistic message appears and list smoothly scrolls to bottom to show it

#### Scenario: Optimistic message is confirmed, scroll remains at bottom
- **WHEN** optimistic (pending) message is confirmed by server and transitions to `confirmed` state
- **THEN** scroll position does not jump; message remains visible as it transitions state

#### Scenario: Multiple rapid messages batch scroll once per batch
- **WHEN** user receives multiple messages in quick succession (within 100ms)
- **THEN** system batches the messages and performs a single smooth scroll to bottom, avoiding animation stutter

### Requirement: Smooth scroll animation uses browser-native behavior
The system SHALL use the browser's native smooth scroll API unless explicitly disabled, providing consistent, performant scrolling across browsers.

#### Scenario: Smooth scroll uses CSS scroll-behavior
- **WHEN** scrolling to bottom is triggered
- **THEN** system calls `element.scrollTo({ top: scrollHeight, behavior: 'smooth' })` to enable smooth animation

#### Scenario: Scroll animation respects user motion preferences
- **WHEN** the device or browser reports `prefers-reduced-motion: reduce`
- **THEN** scroll animation uses instant scroll (no animation) to respect accessibility preference

#### Scenario: Scroll animation completes without interruption
- **WHEN** smooth scroll is in progress and no interaction occurs
- **THEN** animation completes within 300-500ms and leaves scroll position at bottom

### Requirement: Jump to latest button in unread banner integrates with auto-scroll
The "Jump to Latest" button in the unread message banner SHALL use the same scroll logic as auto-scroll to maintain consistency.

#### Scenario: Jump button scrolls even if previously scrolled up
- **WHEN** user is above the bottom (not at bottom) and clicks "Jump to Latest" button
- **THEN** list immediately scrolls to bottom regardless of current scroll position

#### Scenario: Jump button silences new message scroll temporarily
- **WHEN** user clicks "Jump to Latest" and is scrolled to absolute bottom
- **THEN** subsequent new messages continue to auto-scroll until user manually scrolls up away from bottom
