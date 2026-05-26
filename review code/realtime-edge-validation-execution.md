# Realtime Edge Local Validation - Execution Plan

## Overview

This artifact documents the automated validation execution plan for the realtime-edge migration local validation phase.

**Status:** Ready for execution  
**Date:** 2026-05-13  
**Scope:** Single-instance local environment (docker-compose-validation.yml)

---

## Validation Framework

### Components

| Component | Purpose | Type |
|-----------|---------|------|
| Integration Tests | Validate wiring and dependency injection | Automated (JUnit) |
| Docker Compose | Single-instance environment setup | Automated (Docker) |
| PowerShell Scripts | Orchestration and health checks | Automated (Windows) |
| Manual Guide | Runtime behavior validation | Manual |

### Execution Modes

**Mode 1: Fully Automated** (45 minutes)
```powershell
.\validate-realtime-edge-local.ps1 full
```
- Starts environment
- Runs integration tests
- Reports results

**Mode 2: Manual Scripted** (60 minutes)
```powershell
# Step by step
.\validate-realtime-edge-local.ps1 start
.\validate-realtime-edge-local.ps1 status  # Verify health
.\validate-realtime-edge-local.ps1 test    # Run tests
.\validate-realtime-edge-local.ps1 cleanup # Tear down
```

**Mode 3: Manual Execution** (90+ minutes)
- Refer to [realtime-edge-manual-validation-guide.md](realtime-edge-manual-validation-guide.md)
- Perform 7 manual scenarios
- Document results in spreadsheet template

---

## Test Matrix

### Integration Tests

| Test Class | Domain | Coverage | Expected Result |
|------------|--------|----------|-----------------|
| NotificationIntegrationValidationTest | Notification | Command router + delivery service injection | ✅ PASS |
| PresenceIntegrationValidationTest | Presence | Lifecycle controller + delivery injection | ✅ PASS |
| ChatIntegrationValidationTest | Chat | Room router + delivery injection | ✅ PASS |
| FriendshipIntegrationValidationTest | Friendship | Command router + Kafka delivery injection | ✅ PASS |

### Manual Test Scenarios

| Scenario | Domain | Complexity | Time |
|----------|--------|-----------|------|
| Notification Inbound + Outbound | Notification | Low | 5 min |
| Presence Lifecycle + Rooms | Presence | Medium | 8 min |
| Chat Room + DM | Chat | Medium | 10 min |
| Friendship Kafka Topics | Friendship | High | 12 min |
| Cross-Domain Consistency | All | High | 10 min |
| Rollback Paths | All | Low | 5 min |
| Metrics & Logging | All | Low | 5 min |

---

## Execution Steps

### Phase 1: Preparation (5 minutes)

**Pre-flight Checklist:**

- [ ] Windows PowerShell 5.1+ available
- [ ] Docker Desktop installed and running
- [ ] Docker-compose available
- [ ] Gradle wrapper (gradlew.bat) available
- [ ] WebSocket client tool installed (websocat or wscat)
- [ ] Disk space: 5+ GB available
- [ ] Network: 127.0.0.1 (localhost) accessible

**Pre-execution:**

```powershell
# Verify prerequisites
docker ps  # Should list running containers
docker-compose version  # Should show version
.\gradlew.bat --version  # Should show Gradle version
```

### Phase 2: Automated Environment Setup (10-15 minutes)

**Command:**
```powershell
cd d:\Work\PET\chatappPET\chatapp\chatappBE
.\validate-realtime-edge-local.ps1 start
```

**What happens:**
1. Builds services (Docker images)
2. Starts infrastructure: Redis, Zookeeper, Kafka
3. Starts databases: auth-db, user-db, notification-db, presence-db, chat-db, friendship-db
4. Starts domain services: auth, user, notification, presence, chat, friendship
5. Starts realtime-edge-service

**Success indicators:**
- All containers show "healthy" status
- No service startup errors in logs
- All port mappings active

**Health Check:**
```powershell
.\validate-realtime-edge-local.ps1 status
```

Expected output:
```
Docker containers:
  validation-redis              up (healthy)
  validation-kafka             up (healthy)
  validation-auth-service      up (healthy)
  validation-user-service      up (healthy)
  validation-notification...   up (healthy)
  validation-presence-service  up (healthy)
  validation-chat-service      up (healthy)
  validation-friendship-serv.  up (healthy)
  validation-realtime-edge     up (healthy)
```

### Phase 3: Automated Integration Tests (5 minutes)

**Command:**
```powershell
.\validate-realtime-edge-local.ps1 test
```

**Test execution order:**
1. Compile integration validation tests
2. Run NotificationIntegrationValidationTest
3. Run PresenceIntegrationValidationTest
4. Run ChatIntegrationValidationTest
5. Run FriendshipIntegrationValidationTest

**Expected output for each test:**
```
[✓] Notification command router exists and is injectable
[✓] Notification delivery service exists and is injectable
[✓] Session registry available for notification tracking
[✓] Redis template available for notification event ingress
[✓] Notification inbound/outbound paths are wired

...similar for Presence, Chat, Friendship...
```

**Pass/Fail Criteria:**
- ✅ PASS if all test methods complete with assertions passing
- ❌ FAIL if any assertion fails or test throws exception

### Phase 4: Manual Validation Scenarios (45-60 minutes)

**Reference:** [realtime-edge-manual-validation-guide.md](realtime-edge-manual-validation-guide.md)

Execute each scenario in sequence:

1. **Scenario 1: Notification** (5 min)
   - Create users
   - Connect WebSocket
   - Send notification
   - Verify delivery
   - Test mark-read command

2. **Scenario 2: Presence** (8 min)
   - Connect User A
   - Join presence room
   - Connect User B to same room
   - Verify presence updates
   - Test disconnect

3. **Scenario 3: Chat** (10 min)
   - Create chat room
   - Both users join
   - Send room message
   - Verify fanout
   - Test DM extra recipient

4. **Scenario 4: Friendship Kafka** (12 min)
   - Monitor Kafka topics
   - Send friend request
   - Verify topic routing
   - Accept request
   - Verify status change topic

5. **Scenario 5: Cross-Domain** (10 min)
   - Run all 4 domain operations simultaneously
   - Verify no conflicts
   - Check metrics increments

6. **Scenario 6: Rollback Paths** (5 min)
   - Connect to legacy websocket endpoints
   - Send commands directly to domain services
   - Verify bypass of edge works

7. **Scenario 7: Metrics & Logging** (5 min)
   - Check /actuator/metrics
   - Verify counters increment
   - Review logs for ERROR patterns

### Phase 5: Results Documentation (10 minutes)

**Record results in template:**

```markdown
# Manual Validation Results

Date: [DATE]
Validator: [NAME]

## Automated Tests

| Test | Result | Duration |
|------|--------|----------|
| Notification Integration | PASS/FAIL | [ms] |
| Presence Integration | PASS/FAIL | [ms] |
| Chat Integration | PASS/FAIL | [ms] |
| Friendship Integration | PASS/FAIL | [ms] |

## Manual Scenarios

| Scenario | Inbound | Outbound | Issues | Result |
|----------|---------|----------|--------|--------|
| Notification | ✓/✗ | ✓/✗ | [list] | PASS/FAIL |
| Presence | ✓/✗ | ✓/✗ | [list] | PASS/FAIL |
| Chat | ✓/✗ | ✓/✗ | [list] | PASS/FAIL |
| Friendship | ✓/✗ | ✓/✗ | [list] | PASS/FAIL |
| Cross-Domain | - | - | [list] | PASS/FAIL |
| Rollback | ✓/✗ | ✓/✗ | [list] | PASS/FAIL |
| Metrics | ✓/✗ | - | [list] | PASS/FAIL |

## Issues

[List any failures, unexpected behavior, error messages]

## Recommendation

- Proceed to staging: YES/NO
- Blockers to fix: [List if any]
```

### Phase 6: Cleanup (5 minutes)

**Command:**
```powershell
.\validate-realtime-edge-local.ps1 cleanup
```

**Cleans up:**
- Stops all Docker containers
- Removes volumes
- Removes build artifacts

---

## Success Criteria

### Go Signal (Proceed to Staging)

**All of the following must be true:**

- ✅ All 4 integration tests pass
- ✅ Environment health check shows all services healthy
- ✅ All 7 manual scenarios complete without ERROR-level log entries
- ✅ Message delivery latency < 2 seconds for all scenarios
- ✅ Kafka topics exist and receive events (post-hardening contract)
- ✅ Rollback paths functional (legacy endpoints work)
- ✅ Metrics counters increment correctly
- ✅ No message duplication detected
- ✅ Cross-domain consistency verified (no conflicts)

### Stop Signal (Fix Issues First)

**Any of the following triggers a STOP:**

- ❌ Any integration test fails
- ❌ Service fails to start or becomes unhealthy
- ❌ ERROR-level log entries during normal operation
- ❌ Message not delivered to recipient
- ❌ Kafka topic routing incorrect (mismatch with hardening fix)
- ❌ Command reaches wrong domain or rejected
- ❌ Duplication of messages (same message twice)
- ❌ Cross-instance session lookup failed
- ❌ Rollback path broken

---

## Environment Assumptions

### Runtime Configuration

| Setting | Value | Reason |
|---------|-------|--------|
| Session Registry Mode | In-Memory | Local testing (not Redis-backed) |
| Kafka Replication | 1 | Single-instance environment |
| Message TTL | Default | Testing normal operation |
| Timeout Values | Default | Testing standard latencies |

### Resource Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 4 cores | 8 cores |
| RAM | 8 GB | 16 GB |
| Disk | 5 GB free | 10 GB free |
| Network | Localhost only | No external dependencies |

### Network Isolation

- All services run on `validation-net` Docker bridge network
- No external network access required
- All communication on 127.0.0.1 (localhost)
- No firewall rules needed

---

## Troubleshooting Guide

### Container Fails to Start

**Symptom:** Docker shows container exiting immediately

**Investigation:**
```powershell
docker logs validation-SERVICE-NAME
```

**Common causes:**
- Database not ready: Wait longer or check database logs
- Port already in use: Kill process on conflicting port
- Out of disk space: Free up 5+ GB
- Memory pressure: Close other applications

### Health Check Failures

**Symptom:** Status shows "unhealthy" for a service

**Investigation:**
```powershell
docker inspect validation-SERVICE-NAME | Select-String -Pattern "Health"
```

**Common causes:**
- Service still starting (give it 30+ seconds)
- Dependency not healthy (check dependent services first)
- Network connectivity issue: Verify bridge network exists

### Integration Tests Fail

**Symptom:** One or more integration tests show FAILED

**Investigation:**
```powershell
.\gradlew.bat :realtime-edge-service:test --tests "*IntegrationValidationTest" --info
```

**Common causes:**
- Beans not autowired: Check service is healthy and started
- Port not mapped: Verify docker-compose ports section
- Gradle cache stale: Run `.\gradlew.bat clean`

### Manual Scenario Fails

**Symptom:** WebSocket connection refused or message not delivered

**Investigation:**
- Verify service health: `.\validate-realtime-edge-local.ps1 status`
- Check service logs: `docker logs validation-realtime-edge`
- Verify auth token validity
- Check port is accessible: `curl http://localhost:8087/api/v1/health`

---

## Execution Timelines

### Automated Only (No Manual)
- Preparation: 5 min
- Environment setup: 15 min
- Integration tests: 5 min
- Cleanup: 5 min
- **Total: 30 minutes**

### Automated + Manual (Full)
- Preparation: 5 min
- Environment setup: 15 min
- Integration tests: 5 min
- Manual scenarios: 60 min
- Documentation: 10 min
- Cleanup: 5 min
- **Total: 100 minutes (1.5-2 hours)**

### Full Validation (with Repeats/Investigation)
- Expected: 2-3 hours
- If issues found: +30-60 min per issue

---

## Next Steps After Validation

### If All Tests Pass ✅

1. Archive validation results
2. Update readiness decision to "Ready for Staging"
3. Brief staging team on findings
4. Prepare staging environment
5. Move to staging validation phase

### If Issues Found ❌

1. Document exact failure scenario
2. Review logs for root cause
3. Determine if code fix needed or environment adjustment
4. If code fix: Minimal fix only, no redesign
5. Re-run failed test scenario
6. If still failing: Escalate to architecture review
7. Resume validation from Phase 2 (fresh environment)

---

## Sign-Off Template

```
Validation Execution Sign-Off

Executed By: __________________ Date: __________
Supervisor: __________________  Date: __________

Automated Tests: PASS / FAIL
Manual Scenarios: PASS / FAIL
Overall Result: PASS / FAIL

Recommendation: Proceed to Staging / Needs Fixes

Issues Found:
[List if any]

Next Action:
[Stage validation or fix and retry]
```
