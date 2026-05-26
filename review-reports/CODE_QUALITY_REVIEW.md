# Code Quality Review

## Findings (ordered by severity)

### 1) CRITICAL - Compile-time blocker in friendship Kafka producer
- Severity: CRITICAL
- Exact location: `friendship-service/src/main/java/com/chatweb/friendship/infrastructure/kafka/FriendshipEventProducer.java`
- Root cause: Unresolved `TraceContext` reference.
- Impact: Service fails to compile; release pipeline blocked.
- Reproduction risk: Certain.
- Scalability risk: Critical (cannot ship).
- Recommended fix direction: Repair broken symbol/import and add compile gate for module in CI.
- Shared/common changes required: Potentially Yes

### 2) HIGH - Dependency/version management is inconsistent across services
- Severity: HIGH
- Exact location: `realtime-edge-service/build.gradle` vs other service build files
- Root cause: Divergent Spring Cloud BOM versions.
- Impact: Increased maintenance burden and unpredictable transitive behavior.
- Reproduction risk: Medium.
- Scalability risk: High.
- Recommended fix direction: Single platform BOM policy enforced across all modules.
- Shared/common changes required: No

### 3) MEDIUM - Security configuration patterns are duplicated service-by-service
- Severity: MEDIUM
- Exact location: `*/src/main/java/**/SecurityConfig.java`
- Root cause: Repeated CORS/security boilerplate with small variations.
- Impact: Drift and accidental inconsistencies.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Standardize baseline security conventions and enforce via tests/checks.
- Shared/common changes required: Possibly Yes, but local alignment is possible

### 4) MEDIUM - Internal auth filter pattern duplicated with subtle behavior differences
- Severity: MEDIUM
- Exact location: `user-service/.../InternalServiceAuthFilter.java`, `friendship-service/.../InternalServiceAuthFilter.java`, `presence-service/.../InternalServiceIngressAuthFilter.java`
- Root cause: Similar components implemented independently.
- Impact: Inconsistent failure semantics and maintenance overhead.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Normalize trust-boundary behavior and test matrix.
- Shared/common changes required: Potentially Yes, but can be aligned per service first

### 5) MEDIUM - Realtime delivery logic repeats near-identical send envelopes
- Severity: MEDIUM
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/*.java`
- Root cause: Duplicated send and error handling logic across delivery services.
- Impact: Bug-fix fanout and inconsistent instrumentation evolution.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Consolidate send pipeline while preserving domain-specific routing.
- Shared/common changes required: No

### 6) LOW - Mixed package conventions and transitional comments indicate migration debt
- Severity: LOW
- Exact location: multiple service packages and migration TODO comments (example: `notification-service/src/main/java/com/chatweb/notification/infrastructure/websocket/NotificationWebSocketPublisher.java`)
- Root cause: Ongoing architecture transition and phased migration.
- Impact: Higher onboarding complexity and review overhead.
- Reproduction risk: High (organizational).
- Scalability risk: Medium.
- Recommended fix direction: Keep migration ledger and close TODOs with target milestones.
- Shared/common changes required: No
