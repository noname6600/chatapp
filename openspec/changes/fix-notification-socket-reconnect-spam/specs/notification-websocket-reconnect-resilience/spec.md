## ADDED Requirements

### Requirement: Notification websocket reconnect SHALL use bounded exponential backoff
The client MUST schedule reconnect attempts for notification websocket failures using bounded exponential backoff instead of a fixed delay.

#### Scenario: Delay increases after repeated failures
- **WHEN** consecutive reconnect attempts fail for the same notification websocket session signature
- **THEN** each subsequent retry delay increases exponentially until a configured maximum delay is reached

#### Scenario: Delay resets after stable connection
- **WHEN** the notification websocket reconnects and remains open past the configured stability window
- **THEN** retry delay and failure counters reset to initial values for future failures

### Requirement: Notification websocket reconnect SHALL suppress non-recoverable failure loops
The client MUST suppress further reconnect attempts for the current session signature after repeated non-recoverable failures.

#### Scenario: Pre-open failure loop is suppressed
- **WHEN** websocket attempts close before open repeatedly and exceed the configured threshold for one session signature
- **THEN** the client stops scheduling additional reconnect attempts for that same session signature

#### Scenario: Rapid post-open failure loop is suppressed
- **WHEN** websocket opens but closes within the configured instability window repeatedly and exceeds threshold for one session signature
- **THEN** the client suppresses further reconnect attempts for that same session signature

#### Scenario: Suppression does not block new auth session
- **WHEN** access token changes and the session signature changes
- **THEN** suppression state for the prior signature is ignored and reconnect attempts are allowed for the new signature

### Requirement: Notification websocket lifecycle diagnostics SHALL be emitted for reconnect troubleshooting
The client MUST emit structured diagnostics for key reconnect lifecycle transitions.

#### Scenario: Failure diagnostics include close context
- **WHEN** websocket closes unexpectedly
- **THEN** diagnostics include close code, close reason, connection lifetime, failure count, and next retry delay or suppression decision

#### Scenario: Reset diagnostics are emitted
- **WHEN** retry state is reset by stable open, token change, or manual disconnect
- **THEN** diagnostics explicitly report the reset trigger and resulting retry state
