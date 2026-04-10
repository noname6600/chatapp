## MODIFIED Requirements

### Requirement: Optimistic message confirmation SHALL update the existing entry in-place
The system SHALL update the optimistic placeholder message's `messageId`, `seq`, and server-confirmed fields in-place within the store when a `MESSAGE_SENT` event correlates via `clientMessageId`, without the React list component unmounting and remounting. The React list MUST use a key derived from `clientMessageId` (when present) rather than `messageId`, so that the DOM node survives the `messageId` swap from the optimistic value to the server-assigned value. Messages from other users (where `clientMessageId` is absent) MUST continue to use `messageId` as the list key.

#### Scenario: Server confirmation merges into existing placeholder position
- **WHEN** a `MESSAGE_SENT` event arrives with a `clientMessageId` matching an existing optimistic placeholder
- **THEN** the store replaces the placeholder entry with the server-confirmed message and the React component for that message row is updated in place without unmounting

#### Scenario: No unmount occurs during confirmation
- **WHEN** an optimistic message transitions from `"pending"` to `"sent"` via server confirmation
- **THEN** the DOM node for that message row is not destroyed and recreated; only its rendered content updates

#### Scenario: Confirmed message uses server messageId as stable identifier
- **WHEN** the reconciliation completes
- **THEN** the entry's `messageId` equals the server-assigned ID and `deliveryStatus` is `"sent"`

#### Scenario: Messages from other users use messageId as list key
- **WHEN** a real-time message arrives from another user with no `clientMessageId`
- **THEN** the message is rendered in the list using `messageId` as its React key, unchanged from current behavior

#### Scenario: Retry does not cause key collision
- **WHEN** user retries a failed message
- **THEN** the retry reuses the same `clientMessageId` and the list key remains stable — no duplicate-key warning occurs
