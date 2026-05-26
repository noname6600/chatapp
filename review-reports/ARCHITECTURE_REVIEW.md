# Architecture Review

Scope reviewed: gateway-service, auth-service, user-service, chat-service, presence-service, notification-service, upload-service (media equivalent), realtime-edge-service (edge equivalent), plus docker/gradle topology.

## System boundary assessment
- Gateway is the primary HTTP ingress and forwards `/ws/**` to realtime-edge.
- Realtime ownership is centralized in realtime-edge (single WebSocket handler), while domain services expose HTTP/Kafka/Redis integration endpoints.
- Event backbone is mixed Kafka (durable cross-service) + Redis pub/sub (low-latency fanout).
- `media-service` is not present as a module; upload responsibilities are implemented in `upload-service`.

## Findings (ordered by severity)

### 1) CRITICAL - WebSocket route/endpoint boundary mismatch
- Severity: CRITICAL
- Exact location: `gateway-service/src/main/resources/application.yaml` route `realtime-edge-ws` with `Path=/ws/**` and rewrite `/ws/(?<segment>.*) -> /ws/${segment}`; `realtime-edge-service/src/main/java/com/chatweb/realtime/config/WebSocketConfig.java` handler path `/realtime`
- Root cause: Gateway forwards `/ws/*` paths without translating to realtime-edge handler path.
- Impact: WebSocket upgrades can be routed to a non-existent endpoint on edge.
- Reproduction risk: High in environments using `/ws/chat`, `/ws/presence`, `/ws/friendship`, `/ws/notifications` style paths.
- Scalability risk: High because all realtime clients depend on this single route.
- Recommended fix direction: Align gateway rewrite and edge handler contract to one canonical public path.
- Shared/common changes required: No

### 2) HIGH - Inconsistent service naming/boundary contract for media
- Severity: HIGH
- Exact location: `chatappBE/settings.gradle` (contains `upload-service`, no `media-service`)
- Root cause: Operational/review contract references `media-service` while codebase uses `upload-service` for media functions.
- Impact: Misaligned runbooks, dashboards, ownership docs, and incident playbooks.
- Reproduction risk: High in operations and onboarding.
- Scalability risk: Medium (organizational scale and cross-team coordination).
- Recommended fix direction: Standardize naming in architecture docs and deployment contracts.
- Shared/common changes required: No

### 3) HIGH - Realtime-edge dependency governance drift
- Severity: HIGH
- Exact location: `realtime-edge-service/build.gradle` imports `spring-cloud-dependencies:2023.0.0`; other services use Spring Cloud `2025.0.1`
- Root cause: Service-level BOM version diverged from platform baseline.
- Impact: Increased risk of transitive incompatibility, CVE patch lag, and runtime behavior drift.
- Reproduction risk: Medium (appears during upgrades/incidents).
- Scalability risk: High in multi-service release trains.
- Recommended fix direction: Enforce one dependency governance baseline across all services.
- Shared/common changes required: No

### 4) HIGH - Auth persistence strategy inconsistent with rest of platform
- Severity: HIGH
- Exact location: `auth-service/src/main/resources/application.yaml` (`spring.jpa.hibernate.ddl-auto: update`); other services use Flyway + `ddl-auto: validate`
- Root cause: Auth service uses schema auto-mutation at runtime.
- Impact: Non-deterministic schema drift, migration rollback difficulty, prod startup risk.
- Reproduction risk: Medium on startup/deployment.
- Scalability risk: High with concurrent deployments and multi-env promotion.
- Recommended fix direction: Move auth schema lifecycle to versioned migrations like other services.
- Shared/common changes required: No

### 5) MEDIUM - Gateway readiness does not include realtime-edge dependency
- Severity: MEDIUM
- Exact location: `gateway-service/src/main/resources/application.yaml` under `gateway.readiness.required-services`
- Root cause: Realtime-edge is routed by gateway but excluded from readiness dependency checks.
- Impact: Gateway may report readiness while realtime channel is unavailable.
- Reproduction risk: Medium.
- Scalability risk: High for realtime-heavy workloads.
- Recommended fix direction: Include realtime-edge route dependency in readiness health model.
- Shared/common changes required: No

### 6) MEDIUM - Legacy/phase overlap still visible in runtime architecture
- Severity: MEDIUM
- Exact location: `chatappBE/SERVICE_PHASE0_FREEZE_RULES.md` and current runtime modules
- Root cause: Transitional architecture constraints still co-exist with current edge-based realtime model.
- Impact: Architectural ambiguity, higher cognitive load, inconsistent implementation patterns.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Finalize one authoritative realtime ownership contract and retire obsolete path assumptions.
- Shared/common changes required: No
