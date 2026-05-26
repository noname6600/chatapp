# Phase 1F - Upload Confirm Hardening (upload-service only)

## Scope
- Service: upload-service only
- Goal: stop trusting client-supplied confirm metadata and bind confirm to a validated prepare flow.
- Approach used: secure prepare-token correlation + server-side Cloudinary asset verification.

## Files Changed
- chatappBE/upload-service/src/main/java/com/example/upload/service/UploadSigningService.java
- chatappBE/upload-service/src/main/java/com/example/upload/dto/ConfirmUploadRequest.java
- chatappBE/upload-service/src/main/java/com/example/upload/application/ConfirmUploadCommand.java
- chatappBE/upload-service/src/main/java/com/example/upload/application/PrepareUploadResult.java
- chatappBE/upload-service/src/main/java/com/example/upload/dto/PrepareUploadResponse.java
- chatappBE/upload-service/src/main/java/com/example/upload/controller/UploadController.java
- chatappBE/upload-service/src/main/resources/application.yaml
- chatappBE/upload-service/src/test/java/com/example/upload/service/UploadSigningServiceTest.java
- chatappBE/upload-service/src/test/java/com/example/upload/controller/UploadControllerPurposeDeserializationTest.java

## Old Insecure Behavior
- Confirm accepted and validated client-provided metadata fields directly:
  - publicId
  - secureUrl
  - resourceType
  - format
  - bytes
  - width/height
- This allowed a forged client payload to pass if values looked policy-compliant, without proving it matched a prepared upload intent and without provider-side verification of uploaded asset metadata.

## New Verification/Correlation Flow
1. Prepare now issues a signed prepare token (`prepareToken`) tied to:
   - userId
   - purpose
   - publicId
   - folder
   - iat/exp (TTL)
2. Confirm verifies prepare token signature and claims (when token enforcement is enabled):
   - signature valid
   - token not expired
   - user/purpose/publicId/folder match request context
3. Confirm fetches asset metadata from Cloudinary server-side (`cloudinary.api().resource(...)`) and uses provider-verified values as source of truth.
4. Confirm validates provider-verified metadata against upload policy:
   - allowed resource type
   - allowed format
   - max-bytes
   - image dimensions required for image resource type
5. Confirm response metadata is produced from verified provider values, not from client payload values.

## Required Config / Env
Added under `upload.confirm`:
- `upload.confirm.require-prepare-token` (env: `UPLOAD_CONFIRM_REQUIRE_PREPARE_TOKEN`, default: `true`)
- `upload.confirm.prepare-token-secret` (env: `UPLOAD_CONFIRM_PREPARE_TOKEN_SECRET`, default: empty -> fallback to Cloudinary API secret)
- `upload.confirm.prepare-token-ttl-seconds` (env: `UPLOAD_CONFIRM_PREPARE_TOKEN_TTL_SECONDS`, default: `600`)

Operational recommendation:
- Set `UPLOAD_CONFIRM_PREPARE_TOKEN_SECRET` explicitly in production (do not rely on fallback).

## Client Impact
- Prepare response now includes `prepareToken`; clients should persist it and send it in confirm request.
- Confirm request accepts `prepareToken` and, with default config (`require-prepare-token=true`), clients not sending it will be rejected.
- Existing client fields (`secureUrl`, `resourceType`, `format`, `bytes`, dimensions) are no longer trusted as the source of truth; provider-verified metadata is authoritative.

## Regression Risks
- Client rollout risk: older clients that do not send `prepareToken` will fail confirm when token enforcement is enabled.
- Cloudinary API dependency risk: confirm now depends on provider metadata lookup availability/latency.
- Token TTL mismatch risk: if client confirm happens after token expiration, confirm will fail.

Mitigation levers:
- Temporary compatibility mode exists via `UPLOAD_CONFIRM_REQUIRE_PREPARE_TOKEN=false` (documented and logged as weaker posture).
- Tune `UPLOAD_CONFIRM_PREPARE_TOKEN_TTL_SECONDS` for realistic upload durations.

## Verification Gate Run
- Service-only gate executed:
  - `./gradlew :upload-service:test`
- Result: PASS
