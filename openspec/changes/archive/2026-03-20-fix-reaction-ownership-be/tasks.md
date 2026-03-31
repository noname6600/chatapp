## 1. Query & Projection Updates

- [x] 1.1 Add a reaction summary projection that includes `messageId`, `emoji`, `count`, and `reactedByMe` for a given `currentUserId`
- [x] 1.2 Implement repository query logic to compute ownership per emoji (EXISTS/join scoped by `messageIds` + `currentUserId`)
- [x] 1.3 Ensure query returns explicit boolean ownership for each reaction summary row

## 2. DTO & Mapper Updates

- [x] 2.1 Extend reaction response DTO with additive field `reactedByMe`
- [x] 2.2 Update message mapper to map ownership-aware reaction projection into response
- [x] 2.3 Keep existing `emoji` and `count` fields unchanged for backward compatibility

## 3. Message Query Service Integration

- [x] 3.1 Pass authenticated `currentUserId` into message reaction summary query in latest-messages path
- [x] 3.2 Pass authenticated `currentUserId` into message reaction summary query in older-messages path
- [x] 3.3 Verify all message history endpoints that return reactions use ownership-aware summaries

## 4. Toggle Idempotency Hardening

- [x] 4.1 Review toggle pipeline add/remove path to ensure duplicate add does not increase count twice
- [x] 4.2 Review toggle pipeline remove path to ensure duplicate remove does not over-decrement
- [x] 4.3 Confirm published reaction events remain consistent with persisted final toggle state

## 5. Backend Testing

- [x] 5.1 Add query/mapper test: `reactedByMe = true` when current user has reacted
- [x] 5.2 Add query/mapper test: `reactedByMe = false` when current user has not reacted
- [x] 5.3 Add command/pipeline test: first toggle adds and increments exactly once
- [x] 5.4 Add command/pipeline test: second toggle removes and decrements exactly once
- [x] 5.5 Add retry/idempotency test for duplicate add/remove processing

## 6. Validation & Rollout

- [x] 6.1 Run chat-service test suite for message query and reaction modules
- [x] 6.2 Validate API response examples for latest/before endpoints include `reactedByMe`
- [x] 6.3 Confirm frontend compatibility with additive response field in local integration test
- [x] 6.4 Prepare rollback note: revert ownership projection/DTO field if emergency rollback is required
