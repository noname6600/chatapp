# Phase 4 - Presence Redis Operational Check

Date: 2026-05-14
Execution source of truth: review code/service-fix-plan.md
Scope: presence-service only

## Goal
Make Redis keyspace notification and TTL operational dependency explicit and safer for presence offline-by-expiry behavior.

## Implemented Changes

1. Added startup operational check for Redis keyspace notification requirements
- New component:
  - presence-service/src/main/java/com/example/presence/configuration/PresenceRedisKeyspaceNotificationStartupCheck.java
- Behavior:
  - Runs at startup when presence.redis.keyspace-notification.check.enabled=true (default true).
  - Skips check if presence.redis.listener.enabled=false.
  - Reads Redis CONFIG GET notify-keyspace-events.
  - Validates required flags for expired keyevent notifications used by PresenceKeyExpiredListener:
    - requires E and (x or A)
    - practical expected minimal value: Ex
  - On missing/unverifiable config:
    - logs explicit warning by default
    - can fail startup when presence.redis.keyspace-notification.check.fail-on-missing=true

2. Documented operational settings in application config
- Updated:
  - presence-service/src/main/resources/application.yaml
- Added:
  - presence.redis.listener.enabled
  - presence.redis.keyspace-notification.check.enabled
  - presence.redis.keyspace-notification.check.fail-on-missing

3. Added focused unit tests
- New test:
  - presence-service/src/test/java/com/example/presence/configuration/PresenceRedisKeyspaceNotificationStartupCheckTest.java
- Verifies flag policy acceptance/rejection:
  - accepts Ex and KEA
  - rejects missing E, missing x/A, and blank values

## Operational Guidance
For TTL expiry based offline handling through Redis keyevent subscriptions on __keyevent@*__:expired, configure Redis:
- notify-keyspace-events should include keyevent + expired flags, for example: Ex

If managed Redis blocks CONFIG GET, keep default warning mode or use strict mode only when environment supports verification.

## Verification Gate
Most relevant service-local gate for affected scope:
- Command: ./gradlew :presence-service:test
- Result: FAILED (single broad context test failure in ChatappApplicationTests due to missing bean wiring)

Focused gate for changed component:
- Command: ./gradlew :presence-service:test --tests "com.example.presence.configuration.PresenceRedisKeyspaceNotificationStartupCheckTest"
- Result: BUILD SUCCESSFUL
