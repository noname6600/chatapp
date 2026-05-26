# Realtime Edge Local Validation Automation

## What was automated

A self-running local validation harness is now implemented with one main entry command:

```powershell
cd d:\Work\PET\chatappPET\chatapp\chatappBE
.\validate-realtime-edge-local.ps1 full
```

The harness automates:

1. Builds required modules
- Runs Gradle build for required backend services before stack startup in `full` and `start`.
- Uses `:auth-service:bootJar`, `:user-service:bootJar`, `:notification-service:bootJar`, `:presence-service:bootJar`, `:chat-service:bootJar`, `:friendship-service:bootJar`, `:realtime-edge-service:bootJar`.

2. Entry gate verification
- Runs and records all gate commands:
  - multi-domain compile sweep
  - friendship compileTest
  - focused migration tests
  - full edge test suite
- Fails fast on gate failures.

3. Local stack startup
- Uses `chatappBE/docker-compose-validation.yml`.
- Starts redis, zookeeper, kafka, required DBs, auth/user services, migrated domain services, and realtime-edge-service.

4. Automated readiness checks
- Verifies required containers are running.
- Verifies health endpoints:
  - `http://localhost:8081/actuator/health`
  - `http://localhost:8082/actuator/health`
  - `http://localhost:8083/actuator/health`
  - `http://localhost:8084/actuator/health`
  - `http://localhost:8085/actuator/health`
  - `http://localhost:8086/actuator/health`
  - `http://localhost:8087/actuator/health`
- Times out with clear failure if not ready.

5. Automated validation tests
- Runs integration validation tests:
  - `*IntegrationValidationTest`
  - `*NotificationIntegrationValidationTest`
  - `*PresenceIntegrationValidationTest`
  - `*ChatIntegrationValidationTest`
  - `*FriendshipIntegrationValidationTest`

6. Scripted runtime scenarios (where possible)
- Bootstraps test identities through auth/user APIs.
- Runs scripted websocket + event-flow scenarios:
  - notification inbound command + outbound delivery (via Redis publish)
  - presence connect/join/leave/disconnect flow (via websocket + Redis publish)
  - chat room join/send/fanout (via websocket + Redis publish)
  - friendship request/accept topic flow (Kafka offset delta checks)
  - rollback websocket smoke checks against legacy domain endpoints
- Captures pass/fail and latency per scenario.

7. Log and artifact collection
- Collects command logs, docker status, service logs, and edge metrics snapshot.
- Stores run outputs under:
  - `validation-output/<run-id>/`
  - `validation-output/<run-id>/logs/`

8. Automatic report generation
- Writes populated reports with real run data (no placeholders):
  - `review code/realtime-edge-local-validation-results.md`
  - `review code/realtime-edge-local-validation-results.json`
- Includes:
  - entry gate results
  - environment startup status
  - automated test results
  - scenario pass/fail and latency
  - issue classification
  - final recommendation (`ready for staging` | `needs targeted fixes` | `stop and investigate`)

9. Cleanup behavior
- Supports cleanup policy and stack retention controls:
  - `-CleanupPolicy always|never|on-failure`
  - `-KeepStack`

## Supported runner modes

`validate-realtime-edge-local.ps1` now supports:

- `full` (main path): build + gates + start + readiness + tests + scenarios + report + cleanup policy
- `start`: build + start + readiness + report
- `status`: container and endpoint status + report
- `test`: entry gates + integration tests + report
- `scenarios`: scripted runtime scenarios + report
- `report`: regenerate report from latest run state
- `cleanup`: docker compose teardown + report

## Files added/updated

1. Updated runner
- `chatappBE/validate-realtime-edge-local.ps1`

2. Updated compose stack
- `chatappBE/docker-compose-validation.yml`

3. Auto-generated result outputs
- `review code/realtime-edge-local-validation-results.md`
- `review code/realtime-edge-local-validation-results.json`

4. Existing integration scenario test classes retained
- `chatappBE/realtime-edge-service/src/test/java/com/example/realtime/integration/NotificationIntegrationValidationTest.java`
- `chatappBE/realtime-edge-service/src/test/java/com/example/realtime/integration/PresenceIntegrationValidationTest.java`
- `chatappBE/realtime-edge-service/src/test/java/com/example/realtime/integration/ChatIntegrationValidationTest.java`
- `chatappBE/realtime-edge-service/src/test/java/com/example/realtime/integration/FriendshipIntegrationValidationTest.java`

## Current run outcome (latest automated run)

Latest automated run generated real results and identified a blocking gate failure:

- `multi-domain-compile`: PASS
- `friendship-compileTest`: PASS
- `focused-migration-tests`: PASS
- `full-edge-test-suite`: FAIL
- Recommendation: `stop and investigate`

See generated report files for exact durations, exit codes, and artifact paths.

## What still requires manual checks (if needed)

The harness automates primary local validation. Manual checks are still useful for:

1. UX-level websocket behavior verification
- Visual verification of payload semantics from a real client application.

2. Non-deterministic / exploratory checks
- Long-run soak behavior and intermittent race conditions not reliably reproduced in a single scripted pass.

3. Service-specific business assertions beyond current script scope
- Domain-level semantic correctness that requires deeper data assertions than pass/fail transport checks.

## Known limitations of the local harness

1. Fail-fast gate behavior
- If entry gates fail, `full` intentionally stops before stack/scenario execution and reports the blocker.

2. Scenario dependency on local environment behavior
- Runtime scenarios require Docker and successful service startup.
- If local infra is unavailable, report records environment failures with diagnostics.

3. Best-effort websocket semantic assertions
- Script validates delivery and transport-level expectations; it does not replace full product-level acceptance tests.

## How to run

Main one-command execution:

```powershell
cd d:\Work\PET\chatappPET\chatapp\chatappBE
.\validate-realtime-edge-local.ps1 full
```

Common variants:

```powershell
# Keep stack for debugging
.\validate-realtime-edge-local.ps1 full -CleanupPolicy never -KeepStack

# Run only scripted scenarios against an already-running stack
.\validate-realtime-edge-local.ps1 scenarios

# Regenerate report from latest run artifacts
.\validate-realtime-edge-local.ps1 report

# Cleanup stack
.\validate-realtime-edge-local.ps1 cleanup
```

## Output locations

1. Human-readable report
- `review code/realtime-edge-local-validation-results.md`

2. Machine-readable report
- `review code/realtime-edge-local-validation-results.json`

3. Full logs/artifacts per run
- `validation-output/<run-id>/`
- `validation-output/<run-id>/logs/`

This gives a deterministic local automation path for realtime-edge migration validation with one primary command and self-written results.
