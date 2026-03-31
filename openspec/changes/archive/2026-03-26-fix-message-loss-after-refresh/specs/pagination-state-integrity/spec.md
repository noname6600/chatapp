## MODIFIED Requirements

### Requirement: Pagination state remains consistent across scroll cycles
The system SHALL maintain accurate pagination boundaries and message sequence numbers through multiple scroll-load operations, and SHALL preserve newest persisted messages when combining latest and boundary windows.

#### Scenario: Multiple scroll-loads preserve boundary integrity
- **WHEN** user scrolls and loads messages multiple times (cycles 1, 2, 3...)
- **THEN** each cycle correctly updates `oldestSeqByRoom` and maintains a consistent minimum sequence number with no gaps

#### Scenario: Websocket message arrival during scroll-load
- **WHEN** a MESSAGE_SENT event arrives while an older-message fetch is in progress
- **THEN** the new message is correctly appended and does not interfere with the pending prepend operation; both updates apply correctly

#### Scenario: Message gaps are detected and reported
- **WHEN** pagination boundaries show a gap (e.g., oldestSeq jumps from 50 to 40 without loading intermediate messages)
- **THEN** system logs a warning and initiates corrective fetch for missing range if gap threshold exceeded

#### Scenario: Sequence numbers remain unique
- **WHEN** prepending older messages and receiving new messages via websocket
- **THEN** no two messages in the final array share the same sequence number; each message is uniquely identified

#### Scenario: Boundary hydration does not evict latest tail
- **WHEN** unread-boundary hydration loads a middle range outside the latest window
- **THEN** merge behavior preserves the latest persisted tail instead of replacing it
