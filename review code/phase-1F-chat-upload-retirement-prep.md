# Phase 1F - Chat Upload Ownership Retirement Prep (chat-service only)

## Scope
- Service: chat-service only
- Goal: isolate/deprecate direct chat-side avatar upload ownership while preserving fallback safety

## Files Changed
- chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java
- chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java
- chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/CloudinaryService.java
- chatappBE/chat-service/src/main/java/com/example/chat/config/CloudinaryConfig.java
- chatappBE/chat-service/src/main/resources/application.yaml
- chatappBE/chat-service/src/test/java/com/example/chat/modules/room/service/impl/RoomServiceTest.java

## What Was Isolated/Deprecated
- Added a feature gate for legacy direct room avatar upload path:
  - Config key: chat.avatar.legacy-upload.enabled
  - Env override: CHAT_AVATAR_LEGACY_UPLOAD_ENABLED
  - Default: true (fallback preserved)
- Added controller-level block for POST /api/v1/rooms/{roomId}/avatar/legacy-file-upload when flag is disabled.
- Added service-level block in RoomService.uploadAvatar(...) so non-controller callers are also gated.
- Marked legacy Cloudinary direct upload components as deprecated and added warning logs for visibility:
  - CloudinaryService is deprecated and logs usage when called.
  - CloudinaryConfig is deprecated and logs at bean creation.

## Legacy Behavior That Remains
- The legacy direct file-upload endpoint still exists and works when chat.avatar.legacy-upload.enabled=true.
- Direct Cloudinary upload code path is still present for safe fallback.

## What Is Still Required Before Full Removal
- Confirm no active clients depend on /avatar/legacy-file-upload.
- Ensure all avatar clients use upload-service prepare+confirm and then call metadata endpoint:
  - POST /api/v1/rooms/{roomId}/avatar (metadata)
- Add usage monitoring/metrics cutoff window for legacy path.
- After rollout confidence, remove:
  - legacy controller endpoint
  - RoomService.uploadAvatar(...)
  - CloudinaryService/CloudinaryConfig legacy ownership pieces

## Regression Risks
- If flag is turned off before clients migrate, avatar updates via legacy endpoint will fail with BAD_REQUEST.
- Existing fallback remains enabled by default to minimize immediate operational risk.

## Verification
- Command run:
  - ./gradlew :chat-service:test --tests com.example.chat.modules.room.service.impl.RoomServiceTest
- Result:
  - BUILD SUCCESSFUL
- Added test:
  - uploadAvatar_throwsBadRequest_whenLegacyUploadDisabled
