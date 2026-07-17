# Service Layer Freeze Baseline Rules

Status: Approved (architecture freeze baseline)
Date: 2026-05-11

## Scope
Applies to all backend business service modules under `chatappBE/**` except `chatappBE/common/**`.

## Decision
Service layer is freeze-ready at current structure. Until realtime-edge migration is complete, business services remain thin event emitters and orchestrators.

## Freeze Rules
1. Do not add any new browser WebSocket endpoint in business services.
2. Do not add new deployable service-to-service Gradle project dependencies.
3. Do not add new business logic inside WebSocket handlers or Kafka consumers.
4. Do not add new routes under another service's namespace.
5. Do not open new broad package-structure refactor phases across business services.
6. For durable/mixed realtime delivery, do not duplicate service-local WebSocket orchestration patterns; keep fanout simple and defer durability/convergence implementation to realtime-edge migration.

## Allowed in This Phase
1. Clarifying comments and docs.
2. Small safety edits that reduce architectural drift without redesign.
3. Integration verification and end-to-end service flow checks.
4. Realtime-edge migration tasks that remove service-local websocket ownership.

## Out of Scope
1. Broad refactors.
2. New transport layers or endpoint families.
3. Frontend and deployment/infrastructure changes.
