## Context

The system needs a shared upload infrastructure so feature services do not own Cloudinary-specific concerns. Today, chat attachments and user avatar flows require consistent validation, signing, and metadata contracts, but these concerns are not centralized. This change introduces `upload-service` in backend first, then integrates `chat-service`, `user-service`, and frontend upload flows to consume the new contracts.

## Goals / Non-Goals

**Goals:**
- Introduce `upload-service` as a shared microservice for Cloudinary upload preparation and upload metadata confirmation.
- Enforce purpose-based upload policy (folder, file-type constraints, size constraints) at backend contract level.
- Integrate `chat-service` and `user-service` with upload-service metadata flow instead of direct Cloudinary logic.
- Integrate FE flow so clients use backend-issued upload parameters, upload to Cloudinary, then submit asset metadata to business APIs.
- Keep feature business logic inside feature services while upload-service remains infrastructure-only.

**Non-Goals:**
- Moving chat/user domain logic into upload-service.
- Replacing Cloudinary provider in this change.
- Building full media moderation/transcoding pipeline.

## Decisions

1. Dedicated upload-service with signed-upload preparation API
- Decision: upload-service exposes backend API to generate secure upload parameters (signature, timestamp, folder/public-id strategy, constraints) per upload purpose.
- Rationale: centralizes security and policy enforcement; avoids duplicated signing code across services.
- Alternatives considered:
  - Keep Cloudinary logic in each service: rejected due to duplication and inconsistent policy.
  - FE-only unsigned uploads: rejected due to weaker security and inconsistent governance.

2. Purpose-based upload contracts
- Decision: contract includes `purpose` enum (`chat-attachment`, `user-avatar`, extensible) that determines folder path, allowed formats, max size, and transformation presets.
- Rationale: supports shared service while preserving feature-specific upload constraints.

3. Two-step upload flow
- Decision:
  1) FE requests upload prep from upload-service.
  2) FE uploads directly to Cloudinary using signed params.
  3) FE submits returned Cloudinary asset metadata to feature API (chat/user).
- Rationale: avoids proxying file bytes through app servers and keeps data-plane efficient.

4. Metadata ownership and trust boundary
- Decision: feature services accept only validated upload metadata fields expected from upload-service policy (publicId/url/resourceType/format/bytes, etc.).
- Rationale: protects domain services from arbitrary client-provided media claims.

5. Integration order: backend first, then frontend
- Decision: implement upload-service and BE contracts before FE wiring.
- Rationale: FE can integrate against stable APIs; reduces churn.

## Risks / Trade-offs

- [Risk] Cross-service dependency increases operational complexity. -> Mitigation: clear API contract, health checks, fallback error mapping.
- [Risk] Upload failures between prepare and finalize steps create dangling assets. -> Mitigation: TTL strategy and optional cleanup job hooks.
- [Risk] Existing message/avatar flows regress during migration. -> Mitigation: incremental integration and targeted regression tests in chat/user + FE.
- [Risk] Security misconfiguration (signature or preset leakage). -> Mitigation: server-side signing only, strict env validation, and no secret exposure to FE.

## Migration Plan

1. Add `upload-service` module and Cloudinary config/env validation.
2. Implement upload prepare endpoints and purpose policy registry.
3. Add integration clients/contracts in `chat-service` and `user-service`.
4. Switch chat attachment and user avatar endpoints to metadata-driven flow.
5. Integrate FE upload preparation + direct Cloudinary upload + metadata submit.
6. Run backend and frontend regression tests, then cut over.

## Local/Dev Environment Variables

- `CLOUDINARY_CLOUD_NAME` for upload-service Cloudinary account.
- `CLOUDINARY_API_KEY` for upload-service server-side signing.
- `CLOUDINARY_API_SECRET` for upload-service server-side signing.
- `PORT` (optional) to override upload-service default `8088`.
- `MASTER_DATABASE_URL`, `MASTER_DATABASE_USER`, `MASTER_DATABASE_PWD` for `chat-service` and `user-service` DB connectivity.
- `spring.kafka.bootstrap-servers` (via `application.yaml` override) for local Kafka connectivity.
- Redis availability for backend full-suite context tests that initialize Redis listeners.

## Open Questions

- Should upload-service persist upload operation records for audit, or remain stateless in v1?
- Do we need background cleanup for prepared-but-never-finalized uploads in v1?
- What exact per-purpose max size and allowed format matrix should be defaulted for production?

## Local/Dev Environment Variables

- `PORT` for `upload-service` (default `8088`)
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `MASTER_DATABASE_URL`, `MASTER_DATABASE_USER`, `MASTER_DATABASE_PWD` for existing feature services
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` reachability from `upload-service` to `auth-service`
- `spring.data.redis.host`, `spring.data.redis.port` for services/tests that require Redis
