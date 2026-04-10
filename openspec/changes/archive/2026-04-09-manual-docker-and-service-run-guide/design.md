## Context

Local development currently favors running the full backend stack through Docker Compose, which increases CPU and memory usage and causes lag on resource-limited machines. The backend repository already defines infrastructure dependencies (PostgreSQL, Redis, Zookeeper, Kafka) and service modules (`auth-service`, `user-service`, `chat-service`, `presence-service`, `friendship-service`, `notification-service`, `upload-service`, `gateway-service`) that can be launched independently with Gradle `bootRun`.

The existing deployment runbook covers local and VPS flows but does not define a first-class hybrid local mode that uses Docker only for infrastructure while starting application services natively. The requested change adds an explicit operator-facing text runbook with exact startup commands to make this workflow repeatable.

## Goals / Non-Goals

**Goals:**
- Define a deterministic hybrid local startup flow: Docker for infrastructure only, native `bootRun` for services.
- Document exact CLI commands for infrastructure startup, infrastructure checks, and per-service startup.
- Specify startup order and validation checks so developers can quickly recover from partial failures.
- Keep VPS production guidance unchanged while extending local-mode documentation contract.

**Non-Goals:**
- No changes to production Docker Compose behavior.
- No change to service runtime code paths or environment variable schema.
- No orchestration automation beyond documentation updates in this change.

## Decisions

1. Publish a dedicated text runbook for hybrid local mode
- Decision: add a new document at `chatappBE/LOCAL_HYBRID_RUNBOOK.txt`.
- Rationale: the request explicitly asks for another text file with command references; a dedicated file avoids overloading VPS-oriented sections.
- Alternative considered: only patch `chatappBE/DEPLOY.md`.
  - Rejected because mixed concerns make quick local troubleshooting slower.

2. Standardize infrastructure startup to docker compose profile targeting infra services only
- Decision: document one command that starts only `postgres`, `redis`, `zookeeper`, and `kafka` from `docker-compose.local.yml`.
- Rationale: this preserves existing compose contracts while reducing local resource usage by skipping app containers.
- Alternative considered: creating a second compose file just for infra.
  - Deferred to avoid config duplication in this change.

3. Use root Gradle task paths for service startup commands
- Decision: use commands like `./gradlew :auth-service:bootRun` (and Windows equivalent) from `chatappBE` root.
- Rationale: single working directory and shared common modules are easier to manage than per-service shell navigation.
- Alternative considered: per-module `cd <service> && ./gradlew bootRun`.
  - Rejected due to repetitive terminal context switching.

4. Define explicit startup order with dependency-aware verification
- Decision: run and verify infra first, then `auth-service`, then dependent services, then gateway.
- Rationale: reduces boot failures due to unavailable auth/JWK or infra dependencies.
- Alternative considered: arbitrary parallel startup.
  - Rejected because initial startup is less deterministic.

## Risks / Trade-offs

- [Risk] Command drift between docs and compose/build scripts over time. -> Mitigation: require each command in the runbook to correspond to checked-in compose service names and Gradle module paths.
- [Risk] Developers may still start conflicting Docker app containers and native services simultaneously. -> Mitigation: add explicit "do not run app containers in hybrid mode" warning and a cleanup command.
- [Risk] Cloudinary/mail-related env vars can block some services in local boot. -> Mitigation: include preflight section listing required `.env.local` values and optional service skip guidance.
- [Trade-off] Manual multi-terminal startup is more operational work than one-command compose. -> Mitigation: provide copy-paste command blocks and recommended terminal sequence.

## Migration Plan

1. Add the hybrid runbook text file with all CLI commands.
2. Link the new file from local section of deployment docs so it is discoverable.
3. Validate commands on a clean local environment by following documented order.
4. If regressions occur, continue using existing full-compose local startup while correcting docs.

## Open Questions

- Should frontend local start command also be included in the same text file, or remain in frontend docs only?
- Do we want an optional convenience script to open/launch all backend services after this doc-first change?
