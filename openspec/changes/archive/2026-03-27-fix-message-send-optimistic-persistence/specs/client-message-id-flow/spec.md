## MODIFIED Requirements

### Requirement: clientMessageId-based optimistic reconciliation
The frontend SHALL use `clientMessageId` as the primary key to match a server-confirmed message with its optimistic placeholder in local message state, SHALL reconcile on send response, WebSocket events, and history refresh, and SHALL ensure placeholders without durable server match are not shown as sent.

#### Scenario: Server-confirmed message with clientMessageId replaces optimistic placeholder
- **WHEN** server-confirmed message arrives with `clientMessageId` that matches optimistic placeholder
- **THEN** optimistic placeholder is replaced by server-confirmed message without duplication

#### Scenario: Fallback to content-based matching when clientMessageId absent
- **WHEN** server-confirmed message arrives without `clientMessageId`
- **THEN** frontend falls back to matching by `(senderId, content, replyToMessageId)` for reconciliation

#### Scenario: Latest history load marks stale unresolved optimistic placeholder failed
- **WHEN** latest-history refresh completes and no persisted message matches an older pending placeholder by `clientMessageId`
- **THEN** placeholder transitions to `failed` instead of staying as sent/pending indefinitely

#### Scenario: Duplicate confirmations do not create duplicate rendered messages
- **WHEN** both REST and WebSocket confirmations are received for the same `clientMessageId`
- **THEN** local state keeps one canonical message entry
