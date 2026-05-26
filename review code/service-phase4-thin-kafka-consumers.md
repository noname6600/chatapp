# Service Phase4 Thin Kafka Consumers

## 1) Scope
Applied a Kafka consumer thinning pass for the requested service slice:
- user-service
- notification-service
- friendship-service

Goal of this phase: keep consumers as transport adapters (receive/translate/delegate), and move orchestration/idempotency/classification into explicit application-layer handlers.

## 2) Consumers thinned
### user-service
- src/main/java/com/example/user/kafka/AccountCreatedConsumer.java
  - Consumer now only validates envelope/payload and delegates to application service.

### notification-service
- src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java
- src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java
- src/main/java/com/example/notification/kafka/ReactionEventConsumer.java
- src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java
  - All four now perform receive/null-guard/field extraction and delegate.

### friendship-service
- src/main/java/com/example/friendship/kafka/FriendshipEventConsumer.java
- src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java
  - Both are delegate-only adapters in source.

## 3) Application services introduced or clarified
- src/main/java/com/example/user/application/UserKafkaAccountCreatedApplicationService.java
  - Owns account-created idempotency, username allocation, profile create orchestration.

- src/main/java/com/example/notification/application/NotificationKafkaEventApplicationService.java
  - Owns notification event dedupe decisions and delegates domain-specific handling.
  - Refined to use current shared payload contracts and event metadata primitives.

- src/main/java/com/example/friendship/application/FriendshipKafkaEventApplicationService.java
  - Owns friendship event dedupe and event-type/flow classification planning.
  - Refined to compile against current shared contracts without legacy wrapper dependencies.

## 4) Files changed
Primary files changed in this phase:
- user-service/src/main/java/com/example/user/application/UserKafkaAccountCreatedApplicationService.java
- user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java
- notification-service/src/main/java/com/example/notification/application/NotificationKafkaEventApplicationService.java
- notification-service/src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java
- notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java
- notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java
- notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java
- friendship-service/src/main/java/com/example/friendship/application/FriendshipKafkaEventApplicationService.java
- friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventConsumer.java
- friendship-service/src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java

## 5) Validation
### Compile validation
Command:
- .\gradlew.bat :notification-service:compileJava :friendship-service:compileJava :user-service:compileJava --continue --no-daemon

Result:
- BUILD SUCCESSFUL

### Test validation
Command:
- .\gradlew.bat :notification-service:test :friendship-service:test :user-service:test --continue --no-daemon

Result:
- BUILD FAILED (compileTestJava failures in all 3 services)

Observed failure pattern:
- Broad pre-existing test-compile drift to removed/legacy contracts (for example old kafka event wrapper classes and old websocket/common realtime classes).
- One directly related test drift from this phase:
  - user-service AccountCreatedConsumer test constructor/signature is not yet aligned with delegate-style consumer + application service.

## 6) Remaining next steps
1. Align test fixtures in notification/friendship/user services to the current shared event model (`EventEnvelope` + payload contracts) and current websocket/common contracts.
2. Update user-service `AccountCreatedConsumerTest` to mock/delegate through `UserKafkaAccountCreatedApplicationService`.
3. Decide whether phase1 source-set excludes for kafka/websocket slices remain intentional for short-term compile rescue, or should now be progressively removed and fixed module-by-module.
4. After test refactor, rerun:
   - .\gradlew.bat :notification-service:test :friendship-service:test :user-service:test --continue --no-daemon
