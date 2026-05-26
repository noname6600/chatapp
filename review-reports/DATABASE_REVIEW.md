# Database Review

## Findings (ordered by severity)

### 1) HIGH - Auth service uses runtime schema mutation instead of migration discipline
- Severity: HIGH
- Exact location: `auth-service/src/main/resources/application.yaml` (`spring.jpa.hibernate.ddl-auto: update`)
- Root cause: Schema managed implicitly by ORM at runtime.
- Impact: Drift risk between environments and rollback unpredictability.
- Reproduction risk: Medium.
- Scalability risk: High during rolling upgrades.
- Recommended fix direction: Move to versioned migrations + validate mode.
- Shared/common changes required: No

### 2) HIGH - Room owner transfer path is vulnerable to concurrent mutation races
- Severity: HIGH
- Exact location: `chat-service/src/main/java/com/chatweb/chat/modules/room/service/impl/RoomService.java` (`leaveRoom` owner handoff logic) with `RoomMemberRepository.findByRoomId`
- Root cause: Multi-step read-delete-select-update flow without explicit lock in this path.
- Impact: Owner reassignment inconsistency under concurrent leave/remove operations.
- Reproduction risk: Medium under concurrency.
- Scalability risk: High in active rooms.
- Recommended fix direction: Use lock-aware transactional boundary for owner handoff critical section.
- Shared/common changes required: No

### 3) MEDIUM - Repository query patterns can create high query counts for room operations
- Severity: MEDIUM
- Exact location: `chat-service/src/main/java/com/chatweb/chat/modules/room/service/impl/RoomService.java` (repeated `findById`, `findByRoomIdAndUserId`, `findByRoomId` patterns)
- Root cause: Repeated repository calls in command methods with no batching for related checks.
- Impact: Additional DB round-trips for hot commands.
- Reproduction risk: Medium.
- Scalability risk: Medium to High.
- Recommended fix direction: Consolidate access checks/reads for hot paths, profile SQL per command.
- Shared/common changes required: No

### 4) MEDIUM - Session/interaction tables rely on app-level consistency in several paths
- Severity: MEDIUM
- Exact location: `chat-service/src/main/java/com/chatweb/chat/modules/room/repository/RoomMemberRepository.java` and `RoomService.java`
- Root cause: Business invariants enforced in service code with partial DB constraints.
- Impact: Risk of transient inconsistency under retries/concurrency.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Strengthen critical invariants with lock strategy and explicit transactional intent.
- Shared/common changes required: No

### 5) LOW - Broad Hikari defaults may be insufficiently tuned per service role
- Severity: LOW
- Exact location: `*/src/main/resources/application.yaml` (multiple services use similar Hikari pool sizes/timeouts)
- Root cause: Uniform connection settings despite different read/write traffic profiles.
- Impact: Over/under-provisioned DB pools per service.
- Reproduction risk: Low initially.
- Scalability risk: Medium as traffic diversifies.
- Recommended fix direction: Apply role-based DB pool tuning and pool saturation metrics.
- Shared/common changes required: No
