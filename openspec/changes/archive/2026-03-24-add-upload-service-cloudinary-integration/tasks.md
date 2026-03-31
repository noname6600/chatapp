## 1. Upload-Service Backend Foundation

- [x] 1.1 Create `chatappBE/upload-service` module with Spring Boot baseline and service registration/config parity with existing backend services.
- [x] 1.2 Add Cloudinary integration configuration, env validation, and typed settings for credentials and policy defaults.
- [x] 1.3 Implement upload preparation endpoint returning signature, timestamp, folder/public-id policy, and purpose constraints.
- [x] 1.4 Implement metadata confirmation/validation endpoint with normalized asset schema and structured errors.

## 2. Purpose Policy and Security

- [x] 2.1 Implement purpose policy registry (`chat-attachment`, `user-avatar`) with per-purpose folder, formats, and max-size rules.
- [x] 2.2 Enforce unknown-purpose rejection and defensive input validation for prepare/confirm endpoints.
- [x] 2.3 Add security and observability hooks (request logging context, error mapping, health/readiness).

## 3. Backend Consumer Integration

- [x] 3.1 Integrate `chat-service` attachment flow to consume upload-service metadata contract instead of direct Cloudinary concerns.
- [x] 3.2 Integrate `user-service` avatar/profile image flow to consume upload-service metadata contract.
- [x] 3.3 Add/adjust shared DTOs or client adapters in common modules for upload metadata exchange.

## 4. Frontend End-to-End Integration

- [x] 4.1 Implement FE upload preparation API client for upload-service.
- [x] 4.2 Update chat attachment UI flow: prepare -> direct Cloudinary upload -> send message with normalized metadata.
- [x] 4.3 Update user avatar/profile image UI flow: prepare -> direct Cloudinary upload -> submit profile update with normalized metadata.
- [x] 4.4 Add FE error UX for failed upload preparation/upload and retry handling.

## 5. Validation and Rollout

- [x] 5.1 Add unit/integration tests for upload-service prepare/confirm endpoints and purpose policy validation.
- [x] 5.2 Add backend integration tests for chat-service and user-service metadata acceptance/rejection paths.
- [x] 5.3 Add FE integration tests for chat attachment and avatar upload flows using upload-service contracts.
- [x] 5.4 Run full backend and frontend test suites and document required environment variables for local/dev deployment.
