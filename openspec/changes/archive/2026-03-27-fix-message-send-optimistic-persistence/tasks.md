## 1. Send State Model

- [x] 1.1 Add explicit delivery states (`pending`, `sent`, `failed`) to frontend message state/types for optimistic sends.
- [x] 1.2 Implement state transition helpers so optimistic entries can only become `sent` after correlated server confirmation by `clientMessageId`.
- [x] 1.3 Ensure duplicate send triggers for the same draft are suppressed while pending send is in-flight.

## 2. Confirmation And Failure Handling

- [x] 2.1 Update send handlers (REST and WebSocket paths) to include and preserve `clientMessageId` throughout optimistic insert and confirmation handling.
- [x] 2.2 Implement bounded confirmation timeout that transitions unresolved pending optimistic messages to `failed`.
- [x] 2.3 Add retry action for failed optimistic messages that resends using the same original `clientMessageId`.

## 3. Refresh Reconciliation

- [x] 3.1 Reconcile optimistic entries during latest-history refresh so matched server messages replace placeholders without duplication.
- [x] 3.2 Mark unmatched stale optimistic placeholders as `failed` after refresh instead of leaving them as durable-looking sent messages.
- [x] 3.3 Keep legacy fallback reconciliation for messages missing `clientMessageId`.

## 4. UX And Observability

- [x] 4.1 Update message row rendering to show visual states for pending and failed optimistic messages.
- [x] 4.2 Surface retry affordance and failure feedback in composer/message list UX.
- [x] 4.3 Add structured logging/telemetry around send timeout, reconciliation misses, and retry outcomes.

## 5. Verification

- [x] 5.1 Add/adjust unit tests for state transitions (`pending -> sent`, `pending -> failed`, `failed -> retry -> sent`).
- [x] 5.2 Add reconciliation tests for REST/WS dual confirmations and latest-refresh unmatched placeholders.
- [ ] 5.3 Manually verify intermittent-failure flow: optimistic appears, timeout marks failed, retry succeeds, refresh preserves persisted messages only.
