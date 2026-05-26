# Service Phase6: Capability-First Package Reorganization

## 1) Scope

In scope:
- auth-service
- user-service
- chat-service
- friendship-service
- presence-service
- notification-service
- upload-service

Out of scope respected:
- common modules
- gateway
- frontend
- infra/deployment

This pass is incremental and behavior-preserving. The focus is to make domain/application/adapter intent clearer and reduce top-level technical-folder sprawl where low-risk moves were feasible.

## 2) Target structure used

Target pattern applied in this pass:
- capability-first package slices
- inside capability: `domain`, `application`, `adapter/in`, `adapter/out`
- service-wide `config`

Applied structure moves:
- `configuration` -> `config` (service-wide config standardization)
- top-level REST adapter packages moved from `controller` into capability-specific `adapter.in.rest`
- selected `service.impl` classes moved into capability `application`
- selected outbound integrations moved into `adapter.out` packages

## 3) Files/packages moved

### 3.1 Config package standardization (`configuration` -> `config`)
Applied in:
- `auth-service` (`com.example.auth.configuration` -> `com.example.auth.config`)
- `user-service` (`com.example.user.configuration` -> `com.example.user.config`)
- `friendship-service` (`com.example.friendship.configuration` -> `com.example.friendship.config`)
- `presence-service` (`com.example.presence.configuration` -> `com.example.presence.config`)
- `notification-service` (`com.example.notification.configuration` -> `com.example.notification.config`)

Representative files updated:
- `auth-service/.../configuration/SecurityConfig.java`
- `user-service/.../configuration/SecurityConfig.java`
- `friendship-service/.../configuration/SecurityConfig.java`
- `presence-service/.../configuration/WebSocketConfig.java`
- `notification-service/.../configuration/WebSocketConfig.java`

### 3.2 REST adapters moved under capability `adapter.in.rest`
Moved package declarations:
- `com.example.auth.controller` -> `com.example.auth.auth.adapter.in.rest`
  - `AuthController`, `JwksController`
- `com.example.user.controller` -> `com.example.user.profile.adapter.in.rest`
  - `UserProfileController`
- `com.example.upload.controller` -> `com.example.upload.file.adapter.in.rest`
  - `UploadController`
- `com.example.friendship.controller` -> `com.example.friendship.friendship.adapter.in.rest`
  - `FriendController`, `InternalFriendController`
- `com.example.presence.controller` -> `com.example.presence.presence.adapter.in.rest`
  - `PresenceController`
- `com.example.notification.controller` -> `com.example.notification.notification.adapter.in.rest`
  - `NotificationController`, `RoomMuteController`

### 3.3 `service.impl` reductions toward capability `application`
Moved package declarations:
- `auth-service`: `com.example.auth.service.impl.*` -> `com.example.auth.auth.application.*`
  - includes `AuthService`, `AuthSessionService`, `LocalAuthService`, `OAuthAuthService`, `PasswordService`, `TokenService`, etc.
- `friendship-service`: `com.example.friendship.service.impl.*` -> `com.example.friendship.friendship.application.*`
  - `FriendCommandService`, `FriendQueryService`
- `user-service`: `UserProfileService`
  - `com.example.user.service.impl.UserProfileService` -> `com.example.user.profile.application.UserProfileService`

### 3.4 Outbound adapter moves (`adapter.out`)
Moved package declarations:
- `auth-service` account event producer:
  - `com.example.auth.kafka.AccountCreatedEventProducer` -> `com.example.auth.account.adapter.out.kafka.AccountCreatedEventProducer`
- `auth-service` kafka utility config moved into same outbound adapter package:
  - `com.example.auth.kafka.KafkaConfiguration` -> `com.example.auth.account.adapter.out.kafka.KafkaConfiguration`
- `user-service` storage adapter:
  - `com.example.user.service.impl.CloudinaryService` -> `com.example.user.profile.adapter.out.storage.CloudinaryService`

### 3.5 Import/reference updates
- Import and package references were updated in affected main/test sources for moved packages.
- No endpoint paths or runtime behavior contracts were intentionally changed.

## 4) Validation

### Compile
Command run:
- `./gradlew.bat :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :friendship-service:compileJava :presence-service:compileJava :notification-service:compileJava :upload-service:compileJava --continue --no-daemon`

Result:
- `BUILD SUCCESSFUL`

Notes:
- During refactor, BOM/encoding artifacts introduced by bulk rewrite caused transient compile errors.
- Fixed by stripping BOM bytes from edited Java files.
- Re-run compile passed for all scoped services.

### Tests
Command run:
- `./gradlew.bat :auth-service:test :user-service:test :chat-service:test :friendship-service:test :presence-service:test :notification-service:test :upload-service:test --continue --no-daemon`

Result:
- `BUILD FAILED` due `compileTestJava` failures in:
  - `chat-service`
  - `friendship-service`
  - `presence-service`
  - `notification-service`

Observed failure pattern:
- Missing common realtime/websocket/kafka symbols and related classes in test classpaths.
- This matches existing test-source drift/alignment issues seen in earlier phases and is not introduced by endpoint-level behavior changes in this refactor pass.

## 5) Remaining inconsistencies

The architecture is clearer after this pass, but full normalization is still pending:

1. Some technical folders still exist and should be migrated gradually:
- `kafka`, `redis`, `websocket`, `client`, and `service` folders in multiple services.

2. Not all adapters are yet grouped under explicit capability `adapter/in` and `adapter/out` paths:
- Example: many websocket/redis/kafka classes remain in legacy package trees.

3. `chat-service` already has strong capability slicing under `modules/*`, but still uses technical subfolders like `controller`, `infrastructure/*`, and `service/impl` in places that can be further aligned to explicit `adapter/in` and `adapter/out`.

4. Some service-wide package naming and physical directory alignment is still mixed:
- Package declarations are now more standardized, but physical file paths remain legacy in many places (compile-safe in Java, but not yet fully tidy).

5. Existing `I`-prefixed interfaces remain in legacy code.
- No new `I`-prefixed abstractions were introduced in this pass.

## Summary

This phase performs a real, incremental capability-first package reorganization across all scoped services while preserving behavior:
- config package naming standardized
- REST adapters moved under `adapter.in.rest`
- selected `service.impl` classes moved into capability application layers
- selected outbound integrations moved under `adapter.out`
- full scoped compile is green
- tests still fail due pre-existing test classpath/contract drift, documented above
