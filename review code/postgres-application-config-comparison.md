## 1. Summary

Current state is mostly consistent after safe fixes.

- Core and local Docker Compose runtime for DB-backed services is aligned to single Postgres host postgres:5432.
- docker-compose-validation.yml was corrected to use *_DATABASE_URL bindings, removing localhost fallback risk in validation profile.
- Documentation and helper scripts still contain many old multi-Postgres container references.

## 2. Final Expected Architecture

- One Postgres service/container: postgres (chatapp-postgres)
- Postgres port in Docker network: 5432
- Per-service DB names:
  - auth-service -> auth_service
  - user-service -> user_service
  - chat-service -> chat_service
  - friendship-service -> friendship_service
  - notification-service -> notification_service
  - presence-service -> presence_service (if/when datasource is used)
  - upload-service -> upload_service (if/when datasource is used)

Expected Docker JDBC URLs:
- auth-service: jdbc:postgresql://postgres:5432/auth_service
- user-service: jdbc:postgresql://postgres:5432/user_service
- chat-service: jdbc:postgresql://postgres:5432/chat_service
- friendship-service: jdbc:postgresql://postgres:5432/friendship_service
- notification-service: jdbc:postgresql://postgres:5432/notification_service

## 3. Comparison Matrix

| Service | Compose URL | Application default URL | Docker profile URL | Expected URL | Status | Notes |
|---|---|---|---|---|---|---|
| auth-service | AUTH_DATABASE_URL=jdbc:postgresql://postgres:5432/${AUTH_DATABASE_NAME:-auth_service} | ${AUTH_DATABASE_URL:jdbc:postgresql://localhost:5432/auth_service} | MISSING | jdbc:postgresql://postgres:5432/auth_service | OK | Main/local/validation compose now set AUTH_DATABASE_URL correctly. Active profile in local compose: local; in validation compose: validation. |
| user-service | USER_DATABASE_URL=jdbc:postgresql://postgres:5432/${USER_DATABASE_NAME:-user_service} | ${USER_DATABASE_URL:jdbc:postgresql://localhost:5433/user_service} | MISSING | jdbc:postgresql://postgres:5432/user_service | OK | Main/local/validation compose now set USER_DATABASE_URL correctly. Active profile in local compose: local; in validation compose: validation. |
| chat-service | CHAT_DATABASE_URL=jdbc:postgresql://postgres:5432/${CHAT_DATABASE_NAME:-chat_service} | ${CHAT_DATABASE_URL:jdbc:postgresql://localhost:5434/chat_service} | MISSING | jdbc:postgresql://postgres:5432/chat_service | OK | Main/local/validation compose now set CHAT_DATABASE_URL correctly. Active profile in local compose: local; in validation compose: validation. |
| friendship-service | FRIENDSHIP_DATABASE_URL=jdbc:postgresql://postgres:5432/${FRIENDSHIP_DATABASE_NAME:-friendship_service} | ${FRIENDSHIP_DATABASE_URL:jdbc:postgresql://localhost:5436/friendship_service} | MISSING | jdbc:postgresql://postgres:5432/friendship_service | OK | Main/local/validation compose now set FRIENDSHIP_DATABASE_URL correctly. Active profile in local compose: local; in validation compose: validation. |
| notification-service | NOTIFICATION_DATABASE_URL=jdbc:postgresql://postgres:5432/${NOTIFICATION_DATABASE_NAME:-notification_service} | ${NOTIFICATION_DATABASE_URL:jdbc:postgresql://localhost:5435/notification_service} | MISSING | jdbc:postgresql://postgres:5432/notification_service | OK | Main/local/validation compose now set NOTIFICATION_DATABASE_URL correctly. Active profile in local compose: local; in validation compose: validation. |
| presence-service | No datasource URL in compose | No spring.datasource URL in application.yaml | MISSING | (If DB-enabled) jdbc:postgresql://postgres:5432/presence_service | MISSING | Service currently appears Redis/Kafka-only. validation compose injects PRESENCE_DATABASE_* vars that are unused by current app config. |
| upload-service | No datasource URL in compose | No spring.datasource URL in application.yaml | MISSING | (If DB-enabled) jdbc:postgresql://postgres:5432/upload_service | MISSING | Service currently appears Cloudinary-only; no DB config. |
| gateway-service | No datasource URL in compose | No spring.datasource URL in application.yaml | MISSING | N/A | OK | Not DB-backed. |
| realtime-edge-service | No datasource URL in compose | No spring.datasource URL in application.yaml | MISSING | N/A | OK | Not DB-backed in current config. |

## 4. Problems Found

### High

No active high-severity PostgreSQL compose-to-application mismatch remains after applied fixes.

### Medium

1) .env.local still includes legacy per-service DB user/password vars
- file path: chatappBE/.env.local
- property/env name: AUTH_DATABASE_USER/PASSWORD, USER_DATABASE_USER/PASSWORD, CHAT_DATABASE_USER/PASSWORD, FRIENDSHIP_DATABASE_USER/PASSWORD, NOTIFICATION_DATABASE_USER/PASSWORD
- current value: service-specific credentials
- expected value: POSTGRES_USER, POSTGRES_PASSWORD (shared), while keeping service DB names
- why it matters: can still confuse operators if legacy keys are assumed active.
- recommended fix: keep as compatibility only, document as legacy/non-primary.

2) .env.production.example comment drift (resolved)
- file path: chatappBE/.env.production.example
- property/env name: comment block under Database
- current value: updated to shared-credentials wording
- expected value: service-specific DB names with shared postgres credentials
- why it matters: keep doc consistency as env contract evolves.
- recommended fix: none further.

3) POSTGRES_DB=postgres can be misleading with multi-database init approach
- file paths:
  - chatappBE/docker-compose.yml
  - chatappBE/docker-compose.local.yml
  - chatappBE/docker-compose.phase-b-local.yml
  - chatappBE/docker-compose-validation.yml
- property/env name: POSTGRES_DB
- current value: postgres
- expected value: acceptable as bootstrap DB, but should be documented as bootstrap only.
- why it matters: operators may assume only one app DB exists.
- recommended fix: keep value, add docs note.

4) presence-service has no datasource config despite expected mapping list including presence_service
- file path: chatappBE/presence-service/src/main/resources/application.yaml
- property/env name: spring.datasource.* (missing)
- current value: absent
- expected value: either explicit datasource config or explicit statement that presence is Redis-only
- why it matters: architecture expectations can diverge from implementation.
- recommended fix: clarify in docs whether presence is DB-backed or intentionally Redis-only.

5) upload-service has no datasource config despite expected mapping list including upload_service
- file path: chatappBE/upload-service/src/main/resources/application.yaml
- property/env name: spring.datasource.* (missing)
- current value: absent
- expected value: either datasource config or explicit statement that upload is non-DB
- why it matters: same expectation drift risk.
- recommended fix: clarify in docs.

### Low

1) Legacy docs/scripts still mention old multi-Postgres service names
- file paths (examples):
  - chatappBE/DEPLOY.md
  - chatappBE/LOCAL_HYBRID_RUNBOOK.txt
  - chatappBE/scripts/start-services-local.ps1
  - chatappBE/validate-realtime-edge-local.ps1
- current value: references auth-db/user-db/chat-db/friendship-db/notification-db and validation-* DB containers
- expected value: single postgres service references
- why it matters: operator confusion, stale runbooks.
- recommended fix: docs/script cleanup.

2) Legacy script still defines per-role DB bootstrap pattern separate from active docker init mount
- file path: chatappBE/scripts/init-multiple-databases.sh
- property/env name: per-service role creation with AUTH_DATABASE_USER etc
- current value: old role-per-service bootstrap logic
- expected value: align with single shared credential model or mark script as legacy.
- why it matters: accidental use could create inconsistent credentials.
- recommended fix: mark deprecated or update script.

## 5. Old Postgres References

Remaining references detected:

- Old service/container names in docs/scripts:
  - auth-db, user-db, chat-db, friendship-db, notification-db
  - validation-auth-db, validation-user-db, validation-chat-db, validation-friendship-db, validation-notification-db, validation-presence-db
- Old operational commands mentioning removed DB services:
  - chatappBE/LOCAL_HYBRID_RUNBOOK.txt
  - chatappBE/scripts/start-services-local.ps1
  - chatappBE/DEPLOY.md
  - chatappBE/validate-realtime-edge-local.ps1
- Old env var families still present:
  - AUTH_DATABASE_USER/PASSWORD
  - USER_DATABASE_USER/PASSWORD
  - CHAT_DATABASE_USER/PASSWORD
  - FRIENDSHIP_DATABASE_USER/PASSWORD
  - NOTIFICATION_DATABASE_USER/PASSWORD
- Old volume names:
  - Historical names appear in prior docs/report references; active compose runtime now uses postgres-data / phaseb-local-postgres-data only.

## 6. Init Script Check

Init script path:
- chatappBE/docker/postgres/init/01-create-databases.sh

Databases created by script:
- auth_service
- user_service
- chat_service
- friendship_service
- notification_service
- presence_service

Databases required by currently DB-configured services:
- auth_service
- user_service
- chat_service
- friendship_service
- notification_service

Missing databases relative to DB-configured services:
- none

Extra databases relative to DB-configured services:
- presence_service (currently not used by presence-service app config)

Not created by script but in requested mapping:
- upload_service (upload-service currently has no datasource)

Important behavior note:
- docker-entrypoint-initdb.d scripts run only on first initialization of an empty Postgres data directory/volume.

## 7. Recommended Fix Plan

1. Compose env fixes
- Fix docker-compose-validation.yml datasource env binding by setting *_DATABASE_URL to postgres:5432 URLs for DB-backed services.

2. application-docker/prod fixes
- No application-docker.yml/application-prod.yml files found; rely on compose env overrides and optionally add explicit docker profile files if desired.

3. .env.example cleanup
- Update .env.production.example comments and align .env.local with POSTGRES_USER/POSTGRES_PASSWORD shared model.

4. docs cleanup
- Update DEPLOY.md, LOCAL_HYBRID_RUNBOOK.txt, and helper scripts to stop referencing removed per-service DB containers.

5. validation commands
- Run compose config/up/ps/log and in-container env checks listed below.

## 8. Validation Commands

```bash
docker compose config

docker compose up -d postgres

docker compose ps

docker exec -it chatapp-postgres psql -U "$POSTGRES_USER" -d postgres -c "\l"

docker compose up -d auth-service user-service chat-service friendship-service notification-service presence-service upload-service

docker compose logs auth-service --tail=100
docker compose logs user-service --tail=100
docker compose logs chat-service --tail=100
docker compose logs friendship-service --tail=100
docker compose logs notification-service --tail=100
docker compose logs presence-service --tail=100
docker compose logs upload-service --tail=100

docker compose exec auth-service printenv | grep -E "SPRING_DATASOURCE|DB_|POSTGRES|SPRING_PROFILES"
docker compose exec user-service printenv | grep -E "SPRING_DATASOURCE|DB_|POSTGRES|SPRING_PROFILES"
docker compose exec chat-service printenv | grep -E "SPRING_DATASOURCE|DB_|POSTGRES|SPRING_PROFILES"
docker compose exec friendship-service printenv | grep -E "SPRING_DATASOURCE|DB_|POSTGRES|SPRING_PROFILES"
docker compose exec notification-service printenv | grep -E "SPRING_DATASOURCE|DB_|POSTGRES|SPRING_PROFILES"
docker compose exec presence-service printenv | grep -E "SPRING_DATASOURCE|DB_|POSTGRES|SPRING_PROFILES"
docker compose exec upload-service printenv | grep -E "SPRING_DATASOURCE|DB_|POSTGRES|SPRING_PROFILES"
```

## 9. Fixes Applied

1) Validation datasource env binding corrected
- file path: chatappBE/docker-compose-validation.yml
- before:
  - AUTH_DATABASE_HOST/AUTH_DATABASE_PORT/AUTH_DATABASE_NAME
  - USER_DATABASE_HOST/USER_DATABASE_PORT/USER_DATABASE_NAME
  - CHAT_DATABASE_HOST/CHAT_DATABASE_PORT/CHAT_DATABASE_NAME
  - FRIENDSHIP_DATABASE_HOST/FRIENDSHIP_DATABASE_PORT/FRIENDSHIP_DATABASE_NAME
  - NOTIFICATION_DATABASE_HOST/NOTIFICATION_DATABASE_PORT/NOTIFICATION_DATABASE_NAME
- after:
  - AUTH_DATABASE_URL=jdbc:postgresql://postgres:5432/auth_service
  - USER_DATABASE_URL=jdbc:postgresql://postgres:5432/user_service
  - CHAT_DATABASE_URL=jdbc:postgresql://postgres:5432/chat_service
  - FRIENDSHIP_DATABASE_URL=jdbc:postgresql://postgres:5432/friendship_service
  - NOTIFICATION_DATABASE_URL=jdbc:postgresql://postgres:5432/notification_service
- reason: align Compose env keys with Spring datasource binding keys used in application.yaml.

2) Local env shared credential keys added
- file path: chatappBE/.env.local
- before: no POSTGRES_USER/POSTGRES_PASSWORD or PRESENCE_DATABASE_NAME
- after:
  - POSTGRES_USER=postgres
  - POSTGRES_PASSWORD=postgres
  - PRESENCE_DATABASE_NAME=presence_service
- reason: align local env contract with single shared Postgres model.

3) Production example comment updated
- file path: chatappBE/.env.production.example
- before: comment described dedicated per-service DB roles
- after: comment describes shared Postgres credentials + per-service DB names
- reason: remove contract ambiguity.

4) Validation checks rerun
- commands run:
  - docker compose config
  - docker compose -f docker-compose-validation.yml config
- result:
  - Both config renders succeeded (warnings only for unset optional env vars and obsolete version key in validation compose).

## 10. Final Status

PARTIAL

- PASS conditions met:
  - docker compose config passes.
  - only one Postgres service exists per compose file.
  - DB-using services in active compose files now point to postgres:5432.
  - no active compose datasource URL points to localhost or removed DB hostnames.
  - init script creates all currently required DBs for DB-configured services.

- Remaining manual cleanup (non-blocking for runtime):
  - docs/scripts still contain old auth-db/user-db/chat-db/friendship-db/notification-db references.
  - presence-service/upload-service expected DB mapping is not reflected in current app datasource configs (appears intentional by current implementation, but should be documented).