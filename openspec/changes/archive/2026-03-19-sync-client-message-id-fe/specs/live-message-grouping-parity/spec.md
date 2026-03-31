## MODIFIED Requirements

### Requirement: Optimistic reconciliation without duplication
The system SHALL reconcile an optimistic message with its server-confirmed counterpart as a single message
entry. The primary reconciliation key SHALL be `clientMessageId`; content-based matching SHALL be used as a
fallback for messages without `clientMessageId`.

#### Scenario: Server confirmation replaces optimistic message via clientMessageId
- **WHEN** a server-confirmed message arrives with a `clientMessageId` matching an existing optimistic message in the same room
- **THEN** the optimistic message is replaced and no duplicate message is shown

#### Scenario: Server confirmation replaces optimistic message via content fallback
- **WHEN** a server-confirmed message arrives without a `clientMessageId` and its `(senderId, content, replyToMessageId)` matches an optimistic placeholder
- **THEN** the optimistic message is replaced and no duplicate message is shown

#### Scenario: Unmatched server message inserts normally
- **WHEN** a server-confirmed message has no matching optimistic message (by either strategy)
- **THEN** it is inserted once using normal ordering rules
