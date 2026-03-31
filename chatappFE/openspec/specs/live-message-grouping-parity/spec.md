### Requirement: Live and refresh grouping parity
The system SHALL render message grouping identically between live-send view and refreshed/history-loaded view for the same ordered message set in a room.

#### Scenario: Same sender messages group consistently
- **WHEN** two or more messages from the same sender are within the grouping time threshold and have no attachments
- **THEN** the messages are grouped the same way in live view and after refresh

#### Scenario: Attachment boundaries remain consistent
- **WHEN** a message with attachments appears between text messages
- **THEN** grouping boundaries are identical in live view and after refresh

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

### Requirement: Canonical ordering after reconciliation
The system SHALL use canonical message ordering after optimistic replacement so live order converges to refresh order.

#### Scenario: Replacement respects sequence ordering
- **WHEN** an optimistic message is replaced by a server-confirmed message with canonical sequence
- **THEN** the room message list is ordered by canonical sequence values
