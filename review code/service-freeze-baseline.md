# Service Freeze Baseline

## 1. Scope
- Applies to service/app modules under `chatappBE/**` except `chatappBE/common/**`.
- Excludes frontend, deployment/infrastructure, and database redesign.

## 2. Baseline rules
- Service layer is now treated as freeze-ready at the current structure baseline.
- Do not open new broad package-structure refactor phases for business services.
- Do not add new browser websocket endpoints in business services.
- Do not introduce new deployable service-to-service Gradle project dependencies.
- Keep controllers/consumers/adapters thin; do not move business orchestration into websocket handlers or Kafka listeners.
- Future work focus is integration verification, end-to-end service flow proof, and realtime-edge migration.

## 3. Files changed
- `chatappBE/SERVICE_PHASE0_FREEZE_RULES.md`
- `review code/service-freeze-baseline.md`

## 4. What is now frozen
- Current service package/application structure baseline across reviewed business services.
- Current API namespace ownership and service boundary ownership model.
- Current constraint that business services are not the long-term websocket ingress owner.
- Current constraint that service-to-service compile/build coupling does not grow through new deployable module dependencies.

## 5. What work is allowed next
- Integration verification across services (HTTP, Kafka, Redis, gateway routing).
- End-to-end runtime checks for service flows and failure handling.
- Realtime-edge migration tasks that progressively move websocket ingress/fanout ownership out of business services.
- Narrow, production-focused fixes that do not reopen broad structural churn.

## Notes on TODO migration
- Searched service/app scope for explicit TODO markers encouraging service-local websocket expansion.
- No such TODO markers were found in `chatappBE/**` outside `chatappBE/common/**`.
- Existing websocket comments already frame adapters/routes as transitional pending realtime-edge cutover.
