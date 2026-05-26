# DevOps Review

## Findings (ordered by severity)

### 1) HIGH - Redis env mismatch likely breaks notification-service in compose
- Severity: HIGH
- Exact location: `docker-compose.yml` and `docker-compose.local.yml` notification-service env uses `REDIS_HOST/REDIS_PORT`; `notification-service/src/main/resources/application.yaml` reads `SPRING_DATA_REDIS_HOST/SPRING_DATA_REDIS_PORT`
- Root cause: Compose env keys do not match Spring binding keys.
- Impact: Redis-dependent features in notification-service may fail in container runtime.
- Reproduction risk: High.
- Scalability risk: High.
- Recommended fix direction: Align compose env naming with service property contract.
- Shared/common changes required: No

### 2) HIGH - Compile pipeline currently unstable for full backend set
- Severity: HIGH
- Exact location: friendship module compile (`FriendshipEventProducer` unresolved symbol)
- Root cause: Broken source reference in a core producer.
- Impact: Full backend compile gate fails.
- Reproduction risk: Certain.
- Scalability risk: High (blocks release throughput).
- Recommended fix direction: Restore compile integrity before broader rollout.
- Shared/common changes required: Potentially Yes

### 3) MEDIUM - Compose profiles show inconsistent infra behavior
- Severity: MEDIUM
- Exact location: `docker-compose.yml` vs `docker-compose.local.yml` (Redis command/keyspace, dependency conditions)
- Root cause: Runtime options diverge between profiles.
- Impact: Environment-specific bugs and hard-to-reproduce incidents.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Define baseline profile contract and profile-delta documentation.
- Shared/common changes required: No

### 4) MEDIUM - Kafka configuration favors convenience over strict ops control
- Severity: MEDIUM
- Exact location: compose files with `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"`
- Root cause: Auto-topic creation enabled by default.
- Impact: Operational drift and accidental topic proliferation.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Explicit topic bootstrap and startup checks.
- Shared/common changes required: No

### 5) MEDIUM - Gateway readiness excludes realtime-edge dependency
- Severity: MEDIUM
- Exact location: `gateway-service/src/main/resources/application.yaml` readiness list
- Root cause: Health model incomplete for realtime ingress dependency.
- Impact: Partial outage can pass readiness checks.
- Reproduction risk: Medium.
- Scalability risk: High for websocket-heavy workloads.
- Recommended fix direction: Include realtime-edge health in readiness strategy.
- Shared/common changes required: No

### 6) LOW - Deprecated Gradle features detected during compile
- Severity: LOW
- Exact location: Gradle build output (`BUILD SUCCESSFUL` with deprecation warning for Gradle 9 compatibility)
- Root cause: Build scripts/plugins use deprecated features.
- Impact: Future upgrade friction.
- Reproduction risk: High on Gradle major upgrade.
- Scalability risk: Low.
- Recommended fix direction: Run with `--warning-mode all` and retire deprecated usages incrementally.
- Shared/common changes required: No
