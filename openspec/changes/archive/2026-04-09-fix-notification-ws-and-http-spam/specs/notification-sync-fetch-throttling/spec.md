## ADDED Requirements

### Requirement: Notification sync fetches SHALL be single-flight
The client MUST ensure only one in-flight notification sync request (`GET /api/v1/notifications`) is active at a time for automatic lifecycle-driven sync paths.

#### Scenario: Concurrent reconnect triggers are coalesced
- **WHEN** multiple reconnect/open lifecycle events request notification sync while a sync request is already in flight
- **THEN** the client does not start additional parallel sync requests and coalesces to the existing in-flight request

### Requirement: Reconnect-driven notification sync SHALL be rate-limited
The client MUST rate-limit reconnect-driven notification sync fetches using a minimum interval window.

#### Scenario: Rapid reconnect churn does not spam notifications API
- **WHEN** websocket reconnect/open events occur repeatedly within the configured minimum sync interval
- **THEN** the client skips redundant `GET /api/v1/notifications` requests and executes at most one request per interval

#### Scenario: Sync executes again after interval elapses
- **WHEN** a reconnect-driven sync trigger occurs after the minimum interval has elapsed
- **THEN** the client executes a new `GET /api/v1/notifications` request

### Requirement: User-intent sync paths SHALL remain responsive
Rate limits for reconnect churn MUST NOT block explicit user-intent sync actions.

#### Scenario: Manual refresh bypasses reconnect cooldown
- **WHEN** user-triggered or explicit state actions require notification sync
- **THEN** the client executes sync without being blocked by reconnect-trigger cooldown

### Requirement: Sync throttling decisions SHALL be observable
The client MUST emit diagnostics for sync execution and skip decisions.

#### Scenario: Skip decision is logged with reason
- **WHEN** a sync trigger is skipped due to in-flight dedupe or cooldown
- **THEN** diagnostics include trigger reason, skip reason, and elapsed timing context
