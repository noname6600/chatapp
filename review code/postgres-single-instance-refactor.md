# PostgreSQL Single-Instance Refactor Report

Date: 2026-05-14
Scope: chatappBE Docker deployment and DB-related env contracts

## 1) Files Inspected

- docker-compose.yml
- docker-compose.local.yml
- docker-compose.phase-b-local.yml
- docker-compose-validation.yml
- .env.production
- .env.production.example
- scripts/init-multiple-databases.sh
- *-service/src/main/resources/application.yaml
- *-service/src/main/resources/application-local.yaml

Not found in repository:
- docker-compose.override.yml
- docker-compose.prod.yml
- compose.yml / compose.*.yml

## 2) PostgreSQL Containers Found Before Refactor

### docker-compose.yml

1. Service: auth-db
- image: postgres:17-alpine
- container_name: chatapp-auth-db
- exposed ports: none
- volumes: auth-postgres-data:/var/lib/postgresql/data
- POSTGRES_USER: ${AUTH_DATABASE_USER:-auth_user}
- POSTGRES_PASSWORD: ${AUTH_DATABASE_PASSWORD:-auth_password}
- POSTGRES_DB: ${AUTH_DATABASE_NAME:-auth_service}
- healthcheck: pg_isready -U ${AUTH_DATABASE_USER:-auth_user}
- networks: chatapp-net
- depended on by: auth-service
- connected from: auth-service via jdbc:postgresql://auth-db:5432/${AUTH_DATABASE_NAME:-auth_service}

2. Service: user-db
- image: postgres:17-alpine
- container_name: chatapp-user-db
- exposed ports: none
- volumes: user-postgres-data:/var/lib/postgresql/data
- POSTGRES_USER: ${USER_DATABASE_USER:-user_user}
- POSTGRES_PASSWORD: ${USER_DATABASE_PASSWORD:-user_password}
- POSTGRES_DB: ${USER_DATABASE_NAME:-user_service}
- healthcheck: pg_isready -U ${USER_DATABASE_USER:-user_user}
- networks: chatapp-net
- depended on by: user-service
- connected from: user-service via jdbc:postgresql://user-db:5432/${USER_DATABASE_NAME:-user_service}

3. Service: chat-db
- image: postgres:17-alpine
- container_name: chatapp-chat-db
- exposed ports: none
- volumes: chat-postgres-data:/var/lib/postgresql/data
- POSTGRES_USER: ${CHAT_DATABASE_USER:-chat_user}
- POSTGRES_PASSWORD: ${CHAT_DATABASE_PASSWORD:-chat_password}
- POSTGRES_DB: ${CHAT_DATABASE_NAME:-chat_service}
- healthcheck: pg_isready -U ${CHAT_DATABASE_USER:-chat_user}
- networks: chatapp-net
- depended on by: chat-service
- connected from: chat-service via jdbc:postgresql://chat-db:5432/${CHAT_DATABASE_NAME:-chat_service}

4. Service: friendship-db
- image: postgres:17-alpine
- container_name: chatapp-friendship-db
- exposed ports: none
- volumes: friendship-postgres-data:/var/lib/postgresql/data
- POSTGRES_USER: ${FRIENDSHIP_DATABASE_USER:-friendship_user}
- POSTGRES_PASSWORD: ${FRIENDSHIP_DATABASE_PASSWORD:-friendship_password}
- POSTGRES_DB: ${FRIENDSHIP_DATABASE_NAME:-friendship_service}
- healthcheck: pg_isready -U ${FRIENDSHIP_DATABASE_USER:-friendship_user}
- networks: chatapp-net
- depended on by: friendship-service
- connected from: friendship-service via jdbc:postgresql://friendship-db:5432/${FRIENDSHIP_DATABASE_NAME:-friendship_service}

5. Service: notification-db
- image: postgres:17-alpine
- container_name: chatapp-notification-db
- exposed ports: none
- volumes: notification-postgres-data:/var/lib/postgresql/data
- POSTGRES_USER: ${NOTIFICATION_DATABASE_USER:-notification_user}
- POSTGRES_PASSWORD: ${NOTIFICATION_DATABASE_PASSWORD:-notification_password}
- POSTGRES_DB: ${NOTIFICATION_DATABASE_NAME:-notification_service}
- healthcheck: pg_isready -U ${NOTIFICATION_DATABASE_USER:-notification_user}
- networks: chatapp-net
- depended on by: notification-service
- connected from: notification-service via jdbc:postgresql://notification-db:5432/${NOTIFICATION_DATABASE_NAME:-notification_service}

### docker-compose.local.yml

Same 5 database services as above, plus host ports:
- auth-db: 5432:5432
- user-db: 5433:5432
- chat-db: 5434:5432
- friendship-db: 5435:5432
- notification-db: 5436:5432

### docker-compose.phase-b-local.yml

1. Service: notification-db
- image: postgres:17-alpine
- container_name: phaseb-local-notification-db
- exposed ports: 5436:5432
- volumes: phaseb-local-notification-db-data:/var/lib/postgresql/data
- POSTGRES_USER: notification_user
- POSTGRES_PASSWORD: notification_password
- POSTGRES_DB: notification_service
- healthcheck: pg_isready -U notification_user -d notification_service
- depended on by: notification-service
- connected from: notification-service via jdbc:postgresql://notification-db:5432/notification_service

### docker-compose-validation.yml

Six Postgres services were defined:
- auth-db (validation-auth-db, 5432:5432)
- user-db (validation-user-db, 5433:5432)
- notification-db (validation-notification-db, 5436:5432)
- presence-db (validation-presence-db, 5437:5432)
- chat-db (validation-chat-db, 5438:5432)
- friendship-db (validation-friendship-db, 5439:5432)

Each used image postgres:17-alpine, its own POSTGRES_DB/POSTGRES_USER/POSTGRES_PASSWORD, and healthcheck via pg_isready against its own DB.

## 3) Final Target Postgres Service (Implemented)

Single service now used in all compose variants:
- service name: postgres
- container_name: chatapp-postgres
- image: postgres:16-alpine
- internal port: 5432
- external port:
  - docker-compose.yml: not exposed
  - docker-compose.local.yml: 5432:5432
  - docker-compose.phase-b-local.yml: 5432:5432
  - docker-compose-validation.yml: 5432:5432
- persistent volume: postgres-data (or phaseb-local-postgres-data in phase-b local)
- healthcheck: pg_isready -U ${POSTGRES_USER:-postgres} -d postgres
- network: same backend network in each compose file

## 4) Databases Created Inside Shared Instance

Created by init script with idempotent check:
- auth_service
- user_service
- chat_service
- friendship_service
- notification_service
- presence_service

Note:
- upload_service is not created because upload-service currently does not use PostgreSQL.

## 5) Init Script Added

New file:
- docker/postgres/init/01-create-databases.sh

Behavior:
- Runs from /docker-entrypoint-initdb.d on first initialization of empty PGDATA.
- Uses SELECT + \gexec to create databases only when missing.
- Safe to re-run logically, but docker-entrypoint init scripts execute automatically only on first bootstrap of a fresh data directory.

## 6) Compose Refactor Summary

Changed compose files:
- docker-compose.yml
- docker-compose.local.yml
- docker-compose.phase-b-local.yml
- docker-compose-validation.yml

Main changes:
- Removed duplicated Postgres service definitions.
- Added single postgres service definition.
- Updated DB-backed services to use host postgres and port 5432.
- Updated service DB credentials to shared env-based credentials:
  - AUTH_DATABASE_USER/PASSWORD -> ${POSTGRES_USER}/${POSTGRES_PASSWORD}
  - USER_DATABASE_USER/PASSWORD -> ${POSTGRES_USER}/${POSTGRES_PASSWORD}
  - CHAT_DATABASE_USER/PASSWORD -> ${POSTGRES_USER}/${POSTGRES_PASSWORD}
  - FRIENDSHIP_DATABASE_USER/PASSWORD -> ${POSTGRES_USER}/${POSTGRES_PASSWORD}
  - NOTIFICATION_DATABASE_USER/PASSWORD -> ${POSTGRES_USER}/${POSTGRES_PASSWORD}
  - (validation profile also maps PRESENCE_* user/password to same shared credentials)
- Updated depends_on for DB-backed services to:
  - postgres:
    condition: service_healthy
- Removed obsolete per-service Postgres volumes and replaced with single postgres-data volume (phase-b local uses phaseb-local-postgres-data).

## 7) Env Contract Changes

Updated:
- .env.production
- .env.production.example

Now includes shared admin credentials:
- POSTGRES_USER
- POSTGRES_PASSWORD

Database names remain service-specific:
- AUTH_DATABASE_NAME
- USER_DATABASE_NAME
- CHAT_DATABASE_NAME
- FRIENDSHIP_DATABASE_NAME
- NOTIFICATION_DATABASE_NAME
- PRESENCE_DATABASE_NAME

Removed from production env contract:
- AUTH_DATABASE_USER / AUTH_DATABASE_PASSWORD
- USER_DATABASE_USER / USER_DATABASE_PASSWORD
- CHAT_DATABASE_USER / CHAT_DATABASE_PASSWORD
- FRIENDSHIP_DATABASE_USER / FRIENDSHIP_DATABASE_PASSWORD
- NOTIFICATION_DATABASE_USER / NOTIFICATION_DATABASE_PASSWORD

## 8) Spring Configuration Check

Verified application configs continue to use environment-variable overrides:
- auth-service uses AUTH_DATABASE_URL/USER/PASSWORD
- user-service uses USER_DATABASE_URL/USER/PASSWORD
- chat-service uses CHAT_DATABASE_URL/USER/PASSWORD
- friendship-service uses FRIENDSHIP_DATABASE_URL/USER/PASSWORD
- notification-service uses NOTIFICATION_DATABASE_URL/USER/PASSWORD

Result:
- Docker compose now injects URLs with host postgres:5432 and per-service DB names.
- Local defaults in application.yaml/application-local.yaml still reference localhost ports and are preserved for non-container local runs.

## 9) Flyway/Liquibase

No Flyway/Liquibase config changes were made in this refactor.

## 10) Data Safety Warning

Important:
- Old per-service Postgres containers/volumes may contain data.
- Do NOT run docker compose down -v in production unless data loss is explicitly acceptable.
- Before deleting old volumes, dump data first.

Potential old volumes to review before cleanup:
- auth-postgres-data
- user-postgres-data
- chat-postgres-data
- friendship-postgres-data
- notification-postgres-data
- presence-postgres-data (validation)
- phaseb-local-notification-db-data

## 11) Data Migration Commands (Adapted)

### Example dump from old containers

docker exec chatapp-auth-db pg_dump -U auth_user -d auth_service > backup-auth_service.sql

docker exec chatapp-user-db pg_dump -U user_user -d user_service > backup-user_service.sql

docker exec chatapp-chat-db pg_dump -U chat_user -d chat_service > backup-chat_service.sql

docker exec chatapp-friendship-db pg_dump -U friendship_user -d friendship_service > backup-friendship_service.sql

docker exec chatapp-notification-db pg_dump -U notification_user -d notification_service > backup-notification_service.sql

### Restore into new shared postgres

docker exec -i chatapp-postgres psql -U "$POSTGRES_USER" -d auth_service < backup-auth_service.sql

docker exec -i chatapp-postgres psql -U "$POSTGRES_USER" -d user_service < backup-user_service.sql

docker exec -i chatapp-postgres psql -U "$POSTGRES_USER" -d chat_service < backup-chat_service.sql

docker exec -i chatapp-postgres psql -U "$POSTGRES_USER" -d friendship_service < backup-friendship_service.sql

docker exec -i chatapp-postgres psql -U "$POSTGRES_USER" -d notification_service < backup-notification_service.sql

### Validation/phase-b specific old container examples

docker exec validation-presence-db pg_dump -U presence_user -d presence_service > backup-presence_service.sql

docker exec phaseb-local-notification-db pg_dump -U notification_user -d notification_service > backup-phaseb-notification.sql

## 12) Fresh Dev Reset Commands (Use Carefully)

Non-destructive config check:

docker compose config

Start shared postgres only:

docker compose up -d postgres

List DBs:

docker exec -it chatapp-postgres psql -U "$POSTGRES_USER" -d postgres -c "\l"

Start full stack:

docker compose up -d

Service status:

docker compose ps

Logs:

docker compose logs postgres --tail=100

docker compose logs auth-service --tail=100

docker compose logs user-service --tail=100

docker compose logs chat-service --tail=100

docker compose logs friendship-service --tail=100

docker compose logs notification-service --tail=100

docker compose logs presence-service --tail=100

docker compose logs upload-service --tail=100

Destructive reset (dev only, data loss):

docker compose down -v

## 13) Validation Commands Executed in This Task

Executed:
- docker compose config
- docker compose up -d postgres
- docker compose ps

Observed outcome:
- docker compose config rendered successfully (with warnings for unset optional env vars).
- docker compose up -d postgres failed because Docker engine was not reachable on this machine:
  - open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
- docker compose ps failed for the same reason.

This indicates Docker Desktop / daemon was not running (or not accessible) during validation.
