## 1. Define Service-Scoped Database Contract

- [x] 1.1 Add service-specific database environment variables for each DB-backed service in local compose and environment contracts.
- [x] 1.2 Replace shared database credential references in service runtime configuration with service-owned username/password mappings.
- [x] 1.3 Update database bootstrap/init script to create per-service roles, passwords, and grants for auth, user, chat, friendship, and notification databases.

## 2. Update Local and Hybrid Runtime Documentation

- [x] 2.1 Update LOCAL_HYBRID_RUNBOOK.txt with isolated database setup, one-shot role/database creation commands, and per-service credential verification commands.
- [x] 2.2 Update local deployment documentation to reference the new per-service database credential model and migration notes from shared credentials.
- [x] 2.3 Ensure runbook verification commands test role-authenticated access for each service database, not only database existence.

## 3. Align Runtime Configuration Across Services

- [x] 3.1 Update DB-backed service configuration files to consume new service-scoped DB variables consistently across profiles used in local and production-like setups.
- [x] 3.2 Validate that non-DB services (presence-service, upload-service if still DB-less) remain unaffected and clearly documented as out of scope for DB credentials.
- [x] 3.3 Remove or deprecate stale shared database credential references after new variables are wired.

## 4. Verify Isolation End-to-End

- [x] 4.1 Run local infrastructure startup and execute role/database verification commands for each DB-backed service.
- [x] 4.2 Start DB-backed services and confirm successful startup using service-owned credentials.
- [x] 4.3 Validate that cross-service database access is not granted by default and document fallback steps for migration troubleshooting.
