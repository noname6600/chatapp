## 1. Align Environment Contracts

- [x] 1.1 Update `chatappBE/.env.production.example` so DB-backed service keys match local contract field names.
- [x] 1.2 Verify `chatappBE/.env.local` and production example expose the same DB key set for auth, user, chat, friendship, and notification.
- [x] 1.3 Remove or deprecate legacy production-only DB key aliases that duplicate canonical keys.

## 2. Rewire Compose and Runtime Mapping

- [x] 2.1 Update `chatappBE/docker-compose.yml` to consume canonical per-service DB keys aligned with local contract naming.
- [x] 2.2 Confirm `chatappBE/docker-compose.local.yml` and `chatappBE/docker-compose.yml` use matching DB key names for equivalent service wiring.
- [x] 2.3 Verify DB-backed service runtime configs continue to consume `<SERVICE>_DATABASE_URL|USER|PASSWORD` consistently.

## 3. Update Runbook and Verification

- [x] 3.1 Update deployment docs to state that local and production DB key names must match while values may differ by environment.
- [x] 3.2 Add an operator checklist step to compare local vs production DB key parity before startup.
- [x] 3.3 Document deterministic validation commands (`docker compose ... config`) for both local and production contracts.

## 4. Validate and Regressions

- [x] 4.1 Run compose config validation for local and production files after contract updates.
- [x] 4.2 Run a startup smoke check for DB-backed services to ensure no missing-variable regressions from key renaming.
- [x] 4.3 Confirm non-DB services remain unaffected by DB contract alignment changes.
