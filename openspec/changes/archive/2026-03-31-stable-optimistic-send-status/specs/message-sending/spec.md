## ADDED Requirements

### Requirement: Optimistic message confirmation SHALL update the existing entry in-place
The system SHALL update the optimistic placeholder message's `messageId`, `seq`, and server-confirmed fields in-place within the store when a `MESSAGE_SENT` event correlates via `clientMessageId`, without removing and re-inserting the entry. The React component rendering that message MUST NOT be unmounted during the transition from optimistic to confirmed state.

#### Scenario: Server confirmation merges into existing placeholder position
- **WHEN** a `MESSAGE_SENT` event arrives with a `clientMessageId` matching an existing optimistic placeholder
- **THEN** the store replaces the placeholder's key and fields in-place, preserving its position in the sorted list

#### Scenario: Confirmed message uses server messageId as stable key
- **WHEN** the in-place reconciliation completes
- **THEN** the entry's `messageId` equals the server-assigned ID and `deliveryStatus` is `"sent"`

#### Scenario: No unmount occurs during confirmation
- **WHEN** an optimistic message transitions from `"pending"` to `"sent"` via server confirmation
- **THEN** the DOM node for that message is updated, not removed and recreated

#### Scenario: Messages from other users are unaffected by reconciliation logic
- **WHEN** a `MESSAGE_SENT` event has no matching `clientMessageId` in the local store
- **THEN** the message is inserted normally without modifying any existing entry

## MODIFIED Requirements

### Requirement: Unconfirmed optimistic sends SHALL transition to failed with retry option
The system SHALL transition pending optimistic messages to `failed` when confirmation does not arrive before the configured timeout, SHALL display failure state inline on the same line as the message timestamp (not below the message body), and SHALL provide retry and delete actions on that same line.

#### Scenario: Pending optimistic send times out and becomes failed
- **WHEN** pending optimistic message exceeds the configured confirmation timeout without correlated server message
- **THEN** the message transitions to `failed`

#### Scenario: Failed optimistic message exposes retry inline
- **WHEN** message is in `failed` state
- **THEN** UI shows a "Failed to send" label with Retry and Delete actions on the same horizontal line as the message timestamp, not in a separate block below the message content

#### Scenario: Sending status renders on the timestamp line
- **WHEN** message is in `pending` state
- **THEN** UI shows a "Sending…" indicator on the same horizontal line as the message timestamp and the message body height does not change compared to a confirmed message

#### Scenario: Retry keeps idempotency safety
- **WHEN** user retries a failed optimistic message
- **THEN** resend uses the same original `clientMessageId`
