# Service Phase-2 Route Ownership

## 1. Scope
- Included:
  - `chatappBE/notification-service/**`
  - `chatappBE/chat-service/**`
  - `chatappBE/friendship-service/**`
  - `chatappBE/upload-service/**`
  - `chatappBE/gateway-service/**`
- Excluded (respected):
  - `chatappBE/common/**`
  - all other services
  - frontend

## 2. Canonical route ownership chosen
- Chat service owns room routes under: `/api/v1/rooms/**`.
- Notification room-preference routes remain notification-owned under: `/api/v1/notifications/rooms/**`.
- Friendship public API canonical prefix: `/api/v1/friendship/**`.
- Upload public API canonical prefix: `/api/v1/upload/**`.
- Gateway rewrite simplification:
  - removed v1 rewrite compensation for friendship and upload.
  - only legacy non-v1 aliases are rewritten to canonical v1 ownership paths.

## 3. Files changed
- `chatappBE/friendship-service/src/main/java/com/example/friendship/controller/FriendController.java`
  - base route changed from `/api/v1/friends` to `/api/v1/friendship`.
- `chatappBE/upload-service/src/main/java/com/example/upload/controller/UploadController.java`
  - base route changed from `/api/v1/uploads` to `/api/v1/upload`.
- `chatappBE/gateway-service/src/main/resources/application.yaml`
  - friendship rewrite changed from broad `/api(?:/v1)?/friendship -> /api/v1/{segment}` to legacy-only `/api/friendship -> /api/v1/friendship/{segment}`.
  - upload rewrite changed from broad `/api(?:/v1)?/upload -> /api/v1/{segment}` to legacy-only `/api/upload -> /api/v1/upload/{segment}`.
- `chatappBE/upload-service/src/test/java/com/example/upload/controller/UploadControllerPurposeDeserializationTest.java`
  - test endpoint updated to `/api/v1/upload/prepare`.

## 4. Validation
- Executed from `chatappBE`:
  - `./gradlew.bat :notification-service:compileJava :chat-service:compileJava :friendship-service:compileJava :upload-service:compileJava :gateway-service:compileJava --continue --no-daemon`
- Result:
  - `BUILD SUCCESSFUL in 38s`
  - `20 actionable tasks: 2 executed, 18 up-to-date`

## 5. Remaining migration notes
- Legacy gateway aliases are still accepted for backward compatibility and rewritten to canonical paths:
  - `/api/friendship/**` -> `/api/v1/friendship/**`
  - `/api/upload/**` -> `/api/v1/upload/**`
- Clients still calling removed canonical alternatives should migrate:
  - friendship: `/api/v1/friends/**` -> `/api/v1/friendship/**`
  - upload: `/api/v1/uploads/**` -> `/api/v1/upload/**`
- Notification service already avoids generic room namespace ownership (`/api/v1/rooms/**`), so no additional notification controller path change was required in this pass.
