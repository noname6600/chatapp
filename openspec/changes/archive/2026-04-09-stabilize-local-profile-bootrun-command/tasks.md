## 1. Local Profile Completion Audit

- [x] 1.1 Audit backend services for unresolved placeholders when started with `--spring.profiles.active=local`.
- [x] 1.2 Identify missing local profile values for DB, Kafka, Redis, auth JWKS, inter-service URLs, and service-specific third-party properties.

## 2. Local Profile Alignment

- [x] 2.1 Add or update `application-local.yaml` values so each DB-backed service maps to the correct dedicated local DB port and credentials.
- [x] 2.2 Ensure Kafka and Redis local endpoints are consistent across services for native local startup.
- [x] 2.3 Ensure auth, gateway, and inter-service local URLs resolve correctly without dotenv preloading.

## 3. Runbook Command and Troubleshooting Updates

- [x] 3.1 Update local runbook examples to include one-line command usage with `--args="--spring.profiles.active=local"`.
- [x] 3.2 Add troubleshooting guidance for missing local profile values and override patterns.

## 4. Verification

- [x] 4.1 Smoke-test representative command-based startup from cmd (at minimum auth-service and one integration-heavy service).
- [x] 4.2 Verify expected startup signals for database connection, Kafka connectivity, and local port binding.
- [x] 4.3 Confirm no `.env.local` shell-preload step is required for native `bootRun` command workflow.