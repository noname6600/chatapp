# Review Navigation Guide & Master Checklist

**Purpose**: Master reference for navigating all 22 review files and tracking fixes  

---

## 📋 All 22 Review Files

### Executive & Planning (Files 00-02)
1. ✅ **00-executive-summary.md** - High-level findings, metrics, priority overview
2. ✅ **01-build-and-runtime-gates.md** - Compile/startup verification, bean wiring
3. ✅ **02-service-architecture-review.md** - Overall architecture, service boundaries

### Service Reviews (Files 03-11)
4. ✅ **03-gateway-service-review.md** - API Gateway (8080)
5. ✅ **04-auth-service-review.md** - Authentication (8081)
6. ✅ **05-user-service-review.md** - User Profiles (8082)
7. ✅ **06-chat-service-review.md** - Chat & Messaging (8083)
8. ✅ **07-presence-service-review.md** - Online Status (8085)
9. ✅ **08-friendship-service-review.md** - Friend Management (8086)
10. ✅ **09-notification-service-review.md** - Notifications (8087)
11. ✅ **10-upload-service-review.md** - File Upload (8088)
12. ✅ **11-realtime-edge-service-review.md** - Unified WebSocket (8089)

### Cross-Cutting Reviews (Files 12-19)
13. ✅ **12-cross-service-flow-review.md** - REST calls, consistency, compensation
14. ✅ **13-realtime-websocket-kafka-redis-flow-review.md** - Event flows through all channels
15. ✅ **14-security-review.md** - JWT, CORS, auth, uploads, actuator
16. ✅ **15-database-transaction-consistency-review.md** - JPA, N+1, sequences, constraints
17. ✅ **16-api-contract-review.md** - REST endpoints, DTOs, validation, pagination
18. ✅ **17-error-handling-observability-review.md** - Logging, tracing, metrics, observability
19. ✅ **18-test-coverage-review.md** - Unit, integration, WebSocket, concurrency tests
20. ✅ **19-deployment-docker-nginx-config-review.md** - Docker, env vars, healthchecks

### Action & Execution (Files 20-22)
21. ✅ **20-common-touch-minimization-report.md** - Which common modules to change (2 only)
22. ✅ **21-final-fix-plan.md** - Concrete execution order, Phases 1-4
23. ✅ **22-review-navigation-guide.md** - THIS FILE

---

## 🎯 Quick Navigation by Issue Type

### Looking for...

#### Security Issues?
→ Read **14-security-review.md**
- Actuator exposure (HIGH)
- JWT validation gaps (HIGH)
- UserId spoofing (HIGH)
- File upload security (HIGH)
- Rate limiting (MEDIUM)

#### Startup/Compile Problems?
→ Read **01-build-and-runtime-gates.md**
- Bean wiring conflicts (BLOCKER)
- Kafka constants missing (BLOCKER)
- Cloudinary config (BLOCKER)
- Environment variables

#### Database Issues?
→ Read **15-database-transaction-consistency-review.md**
- N+1 queries (HIGH)
- Message seq race condition (HIGH)
- Missing indexes (MEDIUM)
- Concurrency/locking

#### Event Flow Problems?
→ Read **13-realtime-websocket-kafka-redis-flow-review.md**
- Duplicate Kafka listeners (BLOCKER)
- Event dedup missing (HIGH)
- Redis cache invalidation (MEDIUM)
- Kafka error handling (MEDIUM)

#### WebSocket/Realtime Issues?
→ Read **07-presence-service-review.md** and **13-realtime-websocket-kafka-redis-flow-review.md**
- User never goes offline (BLOCKER)
- TTL race condition (HIGH)
- WebSocket lifecycle bugs (MEDIUM)
- JWT handshake auth (MEDIUM)

#### Testing Gaps?
→ Read **18-test-coverage-review.md**
- WebSocket tests (MEDIUM)
- Concurrency tests (MEDIUM)
- Security tests (MEDIUM)
- E2E flow tests (LOW)

#### API/Contract Issues?
→ Read **16-api-contract-review.md**
- Idempotency (HIGH)
- Pagination (MEDIUM)
- Error format (MEDIUM)
- Validation (MEDIUM)

#### Service-Specific Review?
→ Read files **03-11** for individual services
- **03**: Gateway → routing, CORS, rate limiting
- **04**: Auth → JWT, refresh tokens, registration
- **05**: User → profiles, cache invalidation
- **06**: Chat → messages, reactions, WebSocket
- **07**: Presence → online/offline, typing
- **08**: Friendship → requests, blocking
- **09**: Notification → notifications, read state
- **10**: Upload → file security, validation
- **11**: Realtime-edge → consolidated WebSocket

---

## 📊 Issue Summary by Severity

### 🔴 BLOCKER Issues (8 total)
**Must fix before production deployment**

| ID | Issue | File | Status |
|----|-------|------|--------|
| B1 | Kafka topic constants missing | 02, 06, 07, 08, 09 | PHASE 1 |
| B2 | Presence WebSocket user never offline | 07 | PHASE 1 |
| B3 | Duplicate Kafka listeners | 06, 09, 08 | PHASE 1 |
| B4 | Cloudinary bean missing | 10 | PHASE 1 |
| B5 | JWT config incomplete | 04 | PHASE 1 |
| B6 | JwtDecoder singleton conflicts | 01 | PHASE 1 |
| B7 | Redis cache config commented out | 05 | INVESTIGATE |
| B8 | WebSocket UTF-8 BOM | 07, 09 | VERIFY |

### 🔴 HIGH Issues (12 total)
**Security, data corruption, race conditions**

| ID | Issue | File | Phase |
|----|-------|------|-------|
| H1 | JWT signature verification missing | 14, 20 | PHASE 2 |
| H2 | Actuator endpoints unauthenticated | 03, 04, 05, 06, 07, 08, 09, 10, 11, 14 | PHASE 1 |
| H3 | File upload path traversal | 10, 14 | PHASE 2 |
| H4 | Weak file type validation | 10, 14 | PHASE 2 |
| H5 | Presence TTL race condition | 07 | PHASE 1 |
| H6 | UserId spoofing from request body | 14 | PHASE 2 |
| H7 | Message seq race condition | 06, 15 | PHASE 2 |
| H8 | Notification deduplication missing | 09 | PHASE 2 |
| H9 | N+1 query on Room fetch | 06, 15 | PHASE 3 |
| H10 | Kafka error handling missing | 12, 13 | PHASE 3 |
| H11 | Cache invalidation not distributed | 05 | PHASE 3 |
| H12 | Refresh token rotation not enforced | 04 | PHASE 2 |

### 🟠 MEDIUM Issues (18 total)
**Maintainability, edge cases, weak consistency**

Examples: WebSocket lifecycle bugs, event publishing timing, missing indexes, weak transactions, missing tests

### 🟡 LOW Issues (15 total)
**Code quality, naming, cleanup**

Examples: Deprecation warnings, unused variables, inconsistent logging

---

## 🔧 Fix Execution Checklist

### Phase 1: BLOCKER + HIGH (SERVICE-ONLY)

**Status**: NOT STARTED  
**Effort**: 6-8 hours  
**Risk**: LOW

- [ ] Add KafkaTopics constants (common-events)
  - [ ] CHAT_MESSAGE_SENT, CHAT_MESSAGE_EDITED, CHAT_MESSAGE_DELETED
  - [ ] CHAT_REACTION_ADDED, CHAT_REACTION_REMOVED
  - [ ] USER_ONLINE, USER_OFFLINE, USER_TYPING
  - [ ] FRIEND_REQUEST_SENT, FRIEND_REQUEST_ACCEPTED, FRIEND_REQUEST_DECLINED
  - [ ] NOTIFICATION_CREATED, NOTIFICATION_READ, NOTIFICATION_DISMISSED
  - [ ] Verify compilation: `./gradlew.bat clean compileJava --no-daemon`

- [ ] Fix Presence WebSocket lifecycle (presence-service)
  - [ ] Move `unregisterFromRoom()` before `sessionRegistry.removeSession()`
  - [ ] Test disconnect → user marked offline

- [ ] Fix duplicate Kafka listeners (chat, notification, friendship services)
  - [ ] Remove duplicate `@KafkaListener` methods
  - [ ] Consolidate to single listener per topic/group

- [ ] Add Cloudinary config (upload-service)
  - [ ] Create CloudinaryConfig.java
  - [ ] Add application.yaml section
  - [ ] Add docker-compose environment variables
  - [ ] Verify startup: `docker-compose up upload-service`

- [ ] Secure actuator endpoints (all 9 services)
  - [ ] Add `hasRole("ADMIN")` to SecurityConfig for `/actuator/**`
  - [ ] Permit `/actuator/health` without auth
  - [ ] Test: `curl http://localhost:8080/actuator/env` → 401

- [ ] Fix Presence TTL race condition (presence-service)
  - [ ] Add reference counting with `refcount` key
  - [ ] Increment on connect, decrement on disconnect
  - [ ] Only mark offline when refcount = 0
  - [ ] Test: Connect tab 1+2, close tab 1 → still online, close tab 2 → offline

### Phase 2: HIGH (COMMON + SERVICE-ONLY)

**Status**: NOT STARTED  
**Effort**: 4-6 hours  
**Risk**: MEDIUM

- [ ] Add JWT signature verification (common-security)
  - [ ] Update JwtDecoder to verify signature
  - [ ] Test invalid JWT rejected

- [ ] Fix file upload security (upload-service)
  - [ ] Add path traversal validation
  - [ ] Add file type (magic bytes) validation
  - [ ] Add max file size config

- [ ] Audit & fix userId spoofing (all services)
  - [ ] Search all endpoints accepting userId
  - [ ] Verify extracted from JWT, not request body
  - [ ] Document pattern

- [ ] Fix message seq race (chat-service)
  - [ ] Add DB sequence generator or unique constraint
  - [ ] Test concurrent sends → all unique seq

- [ ] Add notification deduplication (notification-service)
  - [ ] Store processed eventIds in Redis
  - [ ] Skip duplicate events

- [ ] Fix refresh token rotation (auth-service)
  - [ ] Verify old token marked revoked
  - [ ] Test old token rejected

### Phase 3: MEDIUM (SERVICE-ONLY)

**Status**: NOT STARTED  
**Effort**: 4-5 hours  
**Risk**: MEDIUM

- [ ] Add @EntityGraph for N+1 prevention
  - [ ] RoomRepository.findById()
  - [ ] UserRepository search methods
  - [ ] Verify query count in logs

- [ ] Add unique constraints
  - [ ] FriendRequest(sender, receiver, status)

- [ ] Fix WebSocket lifecycle (chat, notification services)
- [ ] Verify event publishing timing (all services)
- [ ] Add correlation ID logging

### Phase 4: LOW (OPTIONAL)

**Status**: NOT STARTED  
**Effort**: 2-3 hours

- [ ] Fix deprecation warnings
- [ ] Add missing indexes
- [ ] Remove duplicate @Slf4j (realtime-edge-service)

---

## 📝 Implementation Notes

### Common Modules Changed
**ONLY 2** (as per constraint):
1. `common-events` - Add KafkaTopics constants
2. `common-security` - Add JWT signature verification

**Services Modified** (50+):
- All 9 services: actuator security
- presence-service: lifecycle + TTL
- chat-service: seq + duplicate listeners
- notification-service: duplicate listeners + dedup
- friendship-service: duplicate listeners
- upload-service: Cloudinary + security
- auth-service: refresh token rotation
- And more...

### Compilation Verification

**Full build**:
```bash
./gradlew.bat clean compileJava --no-daemon
```

**Per-service** (if needed):
```bash
./gradlew.bat :presence-service:compileJava --no-daemon
./gradlew.bat :chat-service:test --no-daemon
./gradlew.bat :common:common-security:test --no-daemon
```

### Docker Verification

**Start all services**:
```bash
docker-compose up -d
docker-compose ps
```

**Check logs**:
```bash
docker-compose logs -f gateway
docker-compose logs -f auth-service
```

**Test connectivity**:
```bash
curl -X GET http://localhost:8080/actuator/health
curl -X POST http://localhost:8081/api/auth/login -H "Content-Type: application/json" -d '{...}'
```

---

## 🎬 Next Steps

1. **Review this guide** to understand issue landscape
2. **Start Phase 1** using **21-final-fix-plan.md**
   - Fix in order: KafkaTopics → WebSocket lifecycle → Duplicate listeners → Cloudinary → Actuator → TTL
3. **Verify after each fix** with compilation + docker-compose test
4. **Move to Phase 2** once Phase 1 verified
5. **Document changes** as you go (for rollback if needed)

---

## 📞 Questions?

- **Which file covers X?** Use "🎯 Quick Navigation by Issue Type" above
- **What's the safest first fix?** Phase 1 items (all isolated, LOW risk)
- **What breaks if I skip X?** Check "BLOCKER" column in Executive Summary
- **How do I verify?** Each review file has "Verification Command" section
- **Can I rollback?** Yes, each Phase 1 fix is isolated and reversible

---

**REVIEW COMPLETE**  
**STATUS**: All 22 files created  
**NEXT**: Implement Phase 1 fixes using 21-final-fix-plan.md
