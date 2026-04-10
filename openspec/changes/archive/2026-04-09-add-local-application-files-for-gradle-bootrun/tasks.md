## 1. Local Profile Files

- [x] 1.1 Audit each backend service's current `application.yaml` and existing local profile file to identify which settings still block plain `cmd` `bootRun` startup.
- [x] 1.2 Add or update `application-local.yaml` files for auth-service, user-service, chat-service, presence-service, friendship-service, notification-service, upload-service, and gateway-service with the local hybrid values needed for native startup.
- [x] 1.3 Keep shared base application files environment-oriented and move only local-development overrides into the local profile files.

## 2. Runbook Alignment

- [x] 2.1 Update the local hybrid runbook to require `SPRING_PROFILES_ACTIVE=local` for native Gradle startup.
- [x] 2.2 Replace shell-based `.env.local` startup assumptions in the local runbook with the checked-in local profile file workflow.
- [x] 2.3 Document any remaining values that still require manual override for local development, if placeholders cannot be safely checked in.

## 3. Verification

- [x] 3.1 Validate that each DB-backed service resolves the documented dedicated local database host, port, and credentials through the local profile.
- [x] 3.2 Validate that Redis, Kafka, gateway, and inter-service local URLs remain aligned with the documented hybrid topology.
- [x] 3.3 Smoke-test representative `gradlew.bat :<service>:bootRun` commands from a plain Windows `cmd` workflow and confirm no `.env.local` preload step is required.