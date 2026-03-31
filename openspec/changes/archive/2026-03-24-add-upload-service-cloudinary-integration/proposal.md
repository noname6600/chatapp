## Why

File upload logic is currently fragmented and tied to feature services, which makes consistency, security, and reuse difficult. A dedicated upload-service centralizes Cloudinary upload handling and enables a unified backend-first flow for chat attachments and user profile/avatar media.

## What Changes

- Add a new backend microservice `upload-service` as shared infrastructure for file upload operations.
- Implement upload preparation API that returns secure Cloudinary upload parameters (signature, timestamp, folder/public-id policy, and constraints).
- Support purpose-based upload contexts (for example `chat-attachment` and `user-avatar`) with folder rules and validation limits.
- Add upload completion/metadata confirmation flow so feature services can safely reference uploaded assets.
- Integrate backend consumers:
  - `chat-service` attachment flow uses upload-service metadata instead of direct Cloudinary concerns.
  - `user-service` avatar/profile image flow uses upload-service metadata instead of direct Cloudinary concerns.
- Integrate frontend flow across chat and user profile so clients request upload prep from backend, upload to Cloudinary, then submit resulting asset metadata to feature APIs.
- Add service-level observability, error normalization, and contract tests for upload flow reliability.

## Capabilities

### New Capabilities
- `upload-service-cloudinary-core`: Shared service capability for secure upload preparation, purpose-based folder policy, and metadata contracts.
- `chat-attachment-upload-through-upload-service`: Chat attachment flow using upload-service-backed upload metadata.
- `user-avatar-upload-through-upload-service`: User avatar/profile image flow using upload-service-backed upload metadata.

### Modified Capabilities
- `message-sending`: Attachment submission requirements change to consume upload-service asset metadata instead of direct file upload coupling.

## Impact

- New backend service in `chatappBE/upload-service` with Cloudinary integration and service-level API contracts.
- Backend integration updates in `chat-service` and `user-service` for upload metadata consumption.
- Frontend integration updates in chat attachment and profile/avatar upload flows.
- Shared config/env additions for Cloudinary credentials and upload policy controls.
- New tests: upload-service unit/integration tests, chat/user integration tests, FE flow tests for upload-prep + completion path.
