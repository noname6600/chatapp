# Realtime Edge Migration - Local Validation Framework

## Overview

This is a comprehensive local validation framework for the realtime-edge-service migration across 4 domains: **notification**, **presence**, **chat**, and **friendship**.

**What it does:**
- Automated environment setup (docker-compose)
- Extended integration tests for all 4 domains
- PowerShell orchestration scripts (Windows-first)
- Manual validation guide for runtime behavior
- Pass/fail evidence collection
- Go/no-go decision support

**When to use it:**
- After completing the migration coding phase (Phases A-E ✅ COMPLETE)
- After entry gates are green (4/4 gates ✅ PASS)
- Before moving to staging validation
- For local pre-validation of inbound/outbound paths

**Time required:**
- Fully automated: 30 minutes
- Automated + manual: 100 minutes (1.5-2 hours)
- With investigation: 2-3 hours

---

## Quick Start

### Prerequisites

- Windows PowerShell 5.1+
- Docker Desktop (latest)
- Docker-compose
- 8+ GB RAM, 5+ GB disk space
- `websocat` or `wscat` for manual WebSocket testing

### One-Command Execution

```powershell
cd d:\Work\PET\chatappPET\chatapp\chatappBE
.\validate-realtime-edge-local.ps1 full
```

This will:
1. Start docker-compose environment
2. Wait for all services to be healthy
3. Run all integration tests
4. Report results

**Expected time:** 45 minutes

---

## Framework Components

### 1. Integration Tests (Automated)

**Location:** `realtime-edge-service/src/test/java/com/example/realtime/integration/`

**Files:**
- `NotificationIntegrationValidationTest.java`
- `PresenceIntegrationValidationTest.java`
- `ChatIntegrationValidationTest.java`
- `FriendshipIntegrationValidationTest.java`

**What they test:**
- Component injection (command routers, delivery services)
- Session registry availability
- Kafka/Redis connectivity
- Cross-domain path wiring

**To run manually:**
```bash
cd chatappBE
./gradlew :realtime-edge-service:test --tests "*IntegrationValidationTest" --no-daemon
```

### 2. Docker-Compose Validation Wrapper

**Location:** `chatappBE/docker-compose-validation.yml`

**What it includes:**
- Redis (session registry, pub/sub)
- Kafka + Zookeeper (event broker)
- 2 PostgreSQL databases for each domain (notification, presence, chat, friendship, user, auth)
- All 4 domain services (notification, presence, chat, friendship)
- Auth & User services (dependencies)
- Realtime-edge-service (unified ingress)

**Network:** `validation-net` (isolated bridge network)

**To start manually:**
```bash
docker-compose -f docker-compose-validation.yml up -d
docker-compose -f docker-compose-validation.yml ps  # Check status
```

### 3. PowerShell Orchestration Scripts

**Location:** `chatappBE/validate-realtime-edge-local.ps1`

**Commands:**

```powershell
# Start environment (builds images, starts services)
.\validate-realtime-edge-local.ps1 start

# Run integration tests
.\validate-realtime-edge-local.ps1 test

# Check service health
.\validate-realtime-edge-local.ps1 status

# Clean up containers and volumes
.\validate-realtime-edge-local.ps1 cleanup

# Run full sequence (start + test)
.\validate-realtime-edge-local.ps1 full
```

### 4. Manual Validation Guide

**Location:** `review code/realtime-edge-manual-validation-guide.md`

**Scenarios covered:**
1. Notification inbound + outbound
2. Presence lifecycle + room subscription
3. Chat room + DM fanout
4. Friendship Kafka topic routing (post-hardening)
5. Cross-domain consistency
6. Rollback path verification
7. Metrics & logging verification

**Each scenario includes:**
- Step-by-step instructions
- Expected outcomes
- WebSocket/curl commands
- Troubleshooting guide

### 5. Validation Execution Plan

**Location:** `review code/realtime-edge-validation-execution.md`

**Contains:**
- Full execution sequence with timing
- Success/stop criteria
- Troubleshooting guide
- Resource requirements
- Timeline estimates

### 6. Validation Results Template

**Location:** `review code/realtime-edge-validation-results-template.md`

**Used to capture:**
- Entry gate results
- Environment startup status
- Integration test results (all 4 domains)
- Manual scenario results (7 scenarios)
- Issue classification
- Performance observations
- Final go/no-go recommendation

---

## Usage Flows

### Flow 1: Automated Only (30 min)

**For quick gate validation:**

```powershell
.\validate-realtime-edge-local.ps1 full
```

**Checks:**
- Entry gates (compile, tests)
- Environment health
- All 4 domain integration tests

**Output:**
- Test pass/fail summary
- Environment health status
- Issues (if any)

**Decision criteria:**
- ✅ If all tests pass → Ready for manual validation
- ❌ If any test fails → Investigate and fix

### Flow 2: Automated + Manual (100 min)

**For comprehensive pre-staging validation:**

```powershell
# Phase 1: Automated
.\validate-realtime-edge-local.ps1 start
.\validate-realtime-edge-local.ps1 status  # Verify health

# Phase 2: Manual (refer to guide)
# - Run 7 scenarios from manual validation guide
# - Use websocat to connect to WebSocket
# - Use curl to send commands
# - Monitor Docker logs for errors

# Phase 3: Cleanup
.\validate-realtime-edge-local.ps1 cleanup
```

**Checks:**
- Inbound command routing (all 4 domains)
- Outbound delivery via edge
- Message latency
- Cross-domain interference
- Rollback path functionality
- Metrics recording
- Error log patterns

**Output:**
- Filled-in results template
- Documented issues by severity
- Go/no-go recommendation

### Flow 3: Troubleshooting (60+ min)

**If automated or manual tests fail:**

```powershell
# Keep environment running
.\validate-realtime-edge-local.ps1 start

# Diagnose
docker logs validation-realtime-edge | grep ERROR
docker logs validation-notification-service | grep ERROR
# ... repeat for other services

# Review relevant code
# - Check if issue is code bug or environment/config

# If code fix needed:
# - Make minimal fix only
# - No redesign or new features
# - Recompile and retest

# Restart and retry
docker-compose -f docker-compose-validation.yml restart realtime-edge-service
.\validate-realtime-edge-local.ps1 test
```

---

## Validation Sequence Detail

### Step 1: Entry Gate Verification

```bash
# Run all 4 compile/test gates
./gradlew :realtime-edge-service:compileJava \
  :notification-service:compileJava \
  :presence-service:compileJava \
  :chat-service:compileJava \
  :friendship-service:compileJava --no-daemon

./gradlew :friendship-service:compileTestJava --no-daemon

./gradlew :realtime-edge-service:test --tests "*CommandDispatcher*" \
  --tests "*RestFriendship*" \
  :friendship-service:test --tests "*FriendshipRealtimeCommand*" \
  --tests "*FriendshipEventProducerTopicContract*" --no-daemon

./gradlew :realtime-edge-service:test --no-daemon
```

**Expected:** BUILD SUCCESSFUL for all 4 commands

### Step 2: Environment Startup

```bash
docker-compose -f docker-compose-validation.yml up -d

# Wait for services to become healthy
docker-compose -f docker-compose-validation.yml ps
```

**Expected:**
- All containers `Up` status
- Relevant containers show `healthy`
- No error messages in startup logs

### Step 3: Integration Tests

```bash
./gradlew :realtime-edge-service:test --tests "*IntegrationValidationTest" --no-daemon
```

**Expected:**
- NotificationIntegrationValidationTest: 5/5 passing
- PresenceIntegrationValidationTest: 5/5 passing
- ChatIntegrationValidationTest: 6/6 passing (includes handler compile check)
- FriendshipIntegrationValidationTest: 6/6 passing (includes topic contract check)

### Step 4: Manual Scenarios (Optional but Recommended)

Follow [realtime-edge-manual-validation-guide.md](realtime-edge-manual-validation-guide.md) for:
- 7 domain-specific scenarios
- WebSocket connections
- Command/event flow verification
- Latency measurements
- Error log monitoring

### Step 5: Results Capture

Fill in [realtime-edge-validation-results-template.md](realtime-edge-validation-results-template.md) with:
- Entry gate results
- Environment health status
- Integration test results
- Manual scenario outcomes
- Issue classification
- Final go/no-go decision

---

## Success Criteria

### Go Signal (Proceed to Staging)

**All of the following must be true:**

✅ All 4 entry gates pass  
✅ All services become healthy in docker-compose  
✅ All 4 domain integration tests pass  
✅ All 7 manual scenarios complete without ERROR logs  
✅ Message delivery latency < 2 seconds  
✅ Kafka topics receive events (friendship domain)  
✅ Rollback paths functional (legacy endpoints work)  
✅ Metrics counters increment correctly  
✅ No message duplication detected  
✅ Cross-domain consistency verified

### Stop Signal (Needs Fixes)

**Any of these triggers a STOP:**

❌ Any entry gate fails  
❌ Service fails to start or become healthy  
❌ ERROR-level log entries during normal operation  
❌ Message delivery failure  
❌ Kafka topic routing incorrect  
❌ Command reaches wrong domain  
❌ Message duplication detected  
❌ Cross-instance session lookup failed  
❌ Rollback path broken

---

## Troubleshooting

### Docker Issues

**Problem:** "Docker is not available"
```
Solution: Start Docker Desktop and verify:
  docker ps
  docker-compose version
```

**Problem:** Port already in use
```
Solution: Find and kill process on port:
  netstat -ano | findstr :8087  # Find PID
  taskkill /PID [PID] /F        # Kill process
```

**Problem:** Out of disk space
```
Solution: Free up 5+ GB:
  docker system prune -a
  docker volume prune
```

### Service Health Issues

**Problem:** Service shows "unhealthy"
```
Solution:
  docker logs validation-SERVICE-NAME  # Check logs
  docker inspect validation-SERVICE-NAME  # Check health status
  # Give it 30+ seconds and check again (may still be starting)
```

**Problem:** Database connection failed
```
Solution:
  docker logs validation-DBNAME-db  # Check DB logs
  docker exec validation-DBNAME-db pg_isready -U USER  # Test connectivity
```

### Integration Test Failures

**Problem:** Tests fail with "bean not found"
```
Solution:
  1. Verify service is healthy: docker-compose ps
  2. Check service logs for startup errors: docker logs validation-SERVICE
  3. Rebuild: ./gradlew clean :realtime-edge-service:testClasses
```

**Problem:** Tests fail with "connection refused"
```
Solution:
  1. Verify Redis is healthy: docker logs validation-redis
  2. Verify Kafka is healthy: docker logs validation-kafka
  3. Check docker-compose-validation.yml port mappings
```

### Manual Scenario Issues

**Problem:** WebSocket connection refused
```
Solution:
  1. Check edge service health: curl http://localhost:8087/api/v1/health
  2. Check logs: docker logs validation-realtime-edge | grep ERROR
  3. Verify auth token is valid
```

**Problem:** Message not delivered
```
Solution:
  1. Check delivery service logs for errors
  2. Verify Redis is accepting pub/sub
  3. Check sender/recipient IDs are correct
```

**Problem:** Kafka events not appearing
```
Solution:
  1. Check FriendshipEventProducer logs
  2. Verify Kafka is healthy: docker logs validation-kafka
  3. Check topic exists: docker exec validation-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

---

## Performance Benchmarks

Typical latencies during validation (no load):

| Operation | P50 | P95 | P99 |
|-----------|-----|-----|-----|
| Notification delivery | 100-200ms | 300-500ms | 500-1000ms |
| Presence update | 100-300ms | 400-600ms | 600-1200ms |
| Chat room message | 50-150ms | 200-400ms | 400-800ms |
| Friendship request | 200-400ms | 600-800ms | 800-1500ms |
| DM delivery | 50-150ms | 200-400ms | 400-800ms |

**If observed latencies are 2-3x worse, investigate:**
- Redis performance: `redis-cli info stats`
- Kafka broker: `docker logs validation-kafka`
- Network: Check docker bridge network performance
- System resources: Check CPU/memory utilization

---

## Artifacts Generated

### Before Validation

- ✅ `realtime-edge-full-validation-review.md` - Readiness assessment
- ✅ `realtime-edge-full-validation-decision.md` - Go/no-go decision
- ✅ `realtime-edge-validation-execution.md` - Execution plan (this framework)
- ✅ `realtime-edge-manual-validation-guide.md` - Manual scenario guide
- ✅ `realtime-edge-validation-results-template.md` - Results capture template

### During Validation

- Created: Docker images for all services
- Created: Docker containers for infrastructure + services
- Created: Databases and schemas
- Created: Kafka topics

### After Validation

- Filled-in: `realtime-edge-validation-results-template.md` with actual results
- Generated: Summary of issues by severity
- Generated: Performance observations
- Decision: Go/no-go recommendation

---

## Next Steps After Validation

### If Validation Passes ✅

1. **Archive results:**
   - Copy filled-in results template to `review code/`
   - Prefix with date: `2026-05-13-realtime-edge-validation-results.md`

2. **Update readiness:**
   - Update `realtime-edge-full-validation-decision.md` status to "Ready for Staging"

3. **Brief staging team:**
   - Key findings
   - Known performance characteristics
   - Any accepted tradeoffs

4. **Prepare staging environment:**
   - Two-instance docker-compose setup
   - Staging database seeding (if needed)
   - Monitoring/metrics collection

5. **Proceed to staging validation:**
   - Follow staging validation runbook (to be created)

### If Validation Finds Issues ❌

1. **Classify issues:**
   - Critical: Must fix before staging (blocks go-signal)
   - High: Should fix before staging (improves quality)
   - Medium/Low: Can address post-staging

2. **Fix critical issues:**
   - Minimal code changes only
   - No redesign or new features
   - Re-compile and re-test locally

3. **Re-validate:**
   - Restart fresh environment: `.\validate-realtime-edge-local.ps1 start`
   - Re-run integration tests
   - Re-run failed manual scenarios

4. **Document resolution:**
   - Update results template with fix description
   - Record resolution time
   - Update go/no-go decision

---

## Framework Maintenance

### Adding New Validation Scenarios

1. Create new test class in `realtime-edge-service/src/test/java/com/example/realtime/integration/`
2. Add to integration test execution in `validate-realtime-edge-local.ps1`
3. Document manual steps in `realtime-edge-manual-validation-guide.md`
4. Update results template with new section

### Updating Docker Configuration

1. Edit `docker-compose-validation.yml`
2. Test locally: `docker-compose -f docker-compose-validation.yml up -d`
3. Update PowerShell script health checks if new services added
4. Document changes in this README

### Extending Manual Scenarios

1. Add scenario to `realtime-edge-manual-validation-guide.md`
2. Include step-by-step instructions
3. Define expected outcomes
4. Add troubleshooting section
5. Update results template with new scenario section

---

## Quick Reference

### File Locations

| File | Purpose |
|------|---------|
| `docker-compose-validation.yml` | Single-instance test environment |
| `validate-realtime-edge-local.ps1` | Orchestration script (Windows) |
| `realtime-edge-service/src/test/java/.../integration/` | Integration tests |
| `review code/realtime-edge-validation-execution.md` | Execution plan |
| `review code/realtime-edge-manual-validation-guide.md` | Manual scenarios |
| `review code/realtime-edge-validation-results-template.md` | Results capture |

### Service URLs

| Service | URL | WebSocket |
|---------|-----|-----------|
| Auth | http://localhost:8081 | - |
| User | http://localhost:8082 | - |
| Notification | http://localhost:8083 | ws://localhost:8083/ws/notifications |
| Presence | http://localhost:8084 | ws://localhost:8084/ws/presence |
| Chat | http://localhost:8085 | ws://localhost:8085/ws/chat |
| Friendship | http://localhost:8086 | ws://localhost:8086/ws/friendship |
| Realtime Edge | http://localhost:8087 | ws://localhost:8087/ws/realtime |
| Redis | localhost:6379 | - |
| Kafka | localhost:9092 | - |

### Common Commands

```powershell
# Start environment
.\validate-realtime-edge-local.ps1 start

# Check status
.\validate-realtime-edge-local.ps1 status

# Run tests
.\validate-realtime-edge-local.ps1 test

# Clean up
.\validate-realtime-edge-local.ps1 cleanup

# Full sequence
.\validate-realtime-edge-local.ps1 full
```

---

## Support & Issues

### Getting Help

1. Check troubleshooting section above
2. Review integration test logs: `./gradlew test --info`
3. Check service logs: `docker logs validation-SERVICE-NAME`
4. Review manual validation guide for scenario-specific help

### Reporting Issues

If validation fails with unclear error:

1. Run with verbose output: `.\validate-realtime-edge-local.ps1 -Verbose`
2. Capture full logs: `docker-compose -f docker-compose-validation.yml logs > validation.log`
3. Document: What you were testing, what you expected, what actually happened
4. Share with architecture team for review

---

## Summary

This validation framework provides:

✅ **Automated entry gates** - Compile, test, integration test validation  
✅ **Environment automation** - Docker-compose for all infrastructure + services  
✅ **Orchestration scripts** - PowerShell-first for Windows team  
✅ **Manual scenarios** - Hands-on validation of runtime behavior  
✅ **Results capture** - Structured template for pass/fail evidence  
✅ **Go/no-go guidance** - Clear success/stop criteria  

**Expected outcome:**
- Either clear go-signal for staging validation
- Or identified blockers with fixes needed

**Time investment:**
- 30 min (automated only)
- 100 min (automated + manual, no issues)
- 2-3 hours (with investigation/fixes)

**Next phase:**
- Proceed to multi-instance staging validation if this passes
- Or minimal hardening fix if blockers found

