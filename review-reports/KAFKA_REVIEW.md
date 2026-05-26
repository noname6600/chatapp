# Kafka Review

## Findings (ordered by severity)

### 1) CRITICAL - Friendship service currently does not compile at Kafka producer point
- Severity: CRITICAL
- Exact location: `friendship-service/src/main/java/com/chatweb/friendship/infrastructure/kafka/FriendshipEventProducer.java` (`TraceContext.correlationIdOrEventId(eventId)` unresolved)
- Root cause: Missing symbol/import breaks producer compilation.
- Impact: Friendship Kafka publishing path is blocked; service build fails.
- Reproduction risk: Certain (reproduced by compile).
- Scalability risk: Critical (service cannot be reliably released).
- Recommended fix direction: Restore valid trace context dependency/import path and enforce CI compile gate.
- Shared/common changes required: Potentially Yes (if trace utility moved in shared module), otherwise No

### 2) HIGH - In-memory dedupe for friendship Kafka consumers loses state on restart
- Severity: HIGH
- Exact location: `friendship-service/src/main/java/com/chatweb/friendship/infrastructure/kafka/FriendshipEventDedupeGuard.java`; `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/kafka/FriendshipEventDedupeGuard.java`
- Root cause: Dedup cache stored in process memory with TTL sweep.
- Impact: Replayed events after restart/rebalance can be processed again.
- Reproduction risk: High on restart/redeploy.
- Scalability risk: High with consumer scaling.
- Recommended fix direction: Use external durable dedupe store with bounded retention and consistent keying.
- Shared/common changes required: No

### 3) HIGH - Retry policy is fixed and shallow for multiple consumers
- Severity: HIGH
- Exact location: `notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java`; `user-service/src/main/java/com/chatweb/user/kafka/KafkaConsumerConfig.java`
- Root cause: `FixedBackOff(1000L, 3)` without adaptive backoff.
- Impact: Retry storms during transient dependency outage.
- Reproduction risk: Medium.
- Scalability risk: High under burst failures.
- Recommended fix direction: Apply exponential/jittered backoff and tune per topic criticality.
- Shared/common changes required: No

### 4) MEDIUM - Some consumers use hardcoded topic/group literals
- Severity: MEDIUM
- Exact location: `user-service/src/main/java/com/chatweb/user/kafka/AccountCreatedConsumer.java` (`@KafkaListener(topics = "account.created")`)
- Root cause: Not all listeners use centralized topic constants.
- Impact: Topic governance drift risk.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Standardize topic ownership via shared topic constants/contracts.
- Shared/common changes required: Yes (contract governance), but can start locally

### 5) MEDIUM - Consumer replay posture defaults to earliest in several services
- Severity: MEDIUM
- Exact location: `user-service/src/main/resources/application.yaml`, `chat-service/src/main/resources/application.yaml`, `friendship-service/src/main/resources/application.yaml`, `notification-service/src/main/resources/application.yaml` (`auto-offset-reset: earliest`)
- Root cause: Broad replay-friendly default with no environment-specific gating shown.
- Impact: Unexpected replay load for fresh consumer groups.
- Reproduction risk: Medium.
- Scalability risk: Medium to High.
- Recommended fix direction: Separate bootstrap/recovery profiles from steady-state consumer policy.
- Shared/common changes required: No

### 6) MEDIUM - Notification dedupe accepts null/invalid event IDs as non-duplicate
- Severity: MEDIUM
- Exact location: `notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/NotificationEventDedupeGuard.java`; `notification-service/src/main/java/com/chatweb/notification/application/NotificationKafkaEventApplicationService.java`
- Root cause: `isDuplicate(null)` returns false; malformed IDs bypass dedupe.
- Impact: Duplicate processing risk when metadata is malformed.
- Reproduction risk: Medium (depends on producer correctness).
- Scalability risk: Medium.
- Recommended fix direction: Treat missing/invalid IDs as reject or quarantine path.
- Shared/common changes required: No

### 7) LOW - Kafka auto topic creation enabled in compose environments
- Severity: LOW
- Exact location: `docker-compose.yml`, `docker-compose.local.yml`, `docker-compose.phase-b-local.yml`, `docker-compose-validation.yml` (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"`)
- Root cause: Infra convenience setting may leak into uncontrolled environments.
- Impact: Silent topic typos create orphan topics.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Explicit topic provisioning and startup validation.
- Shared/common changes required: No
