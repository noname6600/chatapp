# Service Phase-3 Service Boundary Cleanup

## 1. Scope
- Included:
  - `chatappBE/user-service/**`
  - `chatappBE/upload-service/**`
- Out of scope respected:
  - `chatappBE/common/**`
  - all other services
  - gateway
  - frontend

## 2. Dependency removed
- Removed compile-time project dependency from user-service to upload-service:
  - removed `implementation project(':upload-service')` from `chatappBE/user-service/build.gradle`.

## 3. Files changed
- `chatappBE/user-service/build.gradle`
- `chatappBE/user-service/src/main/java/com/example/user/service/impl/UserProfileService.java`
- `chatappBE/user-service/src/main/java/com/example/user/dto/AvatarAssetMetadata.java`

## 4. Replacement boundary model used
- Replaced direct usage of `com.example.upload.contract.UploadAssetMetadata` inside user-service with user-owned boundary DTO:
  - `com.example.user.dto.AvatarAssetMetadata`
- Mapping/validation flow preserved in user-service:
  - `AvatarMetadataRequest` -> `AvatarAssetMetadata` -> existing avatar validation/persistence logic.
- Upload-service remains an external service/API boundary (no compile-time class import from user-service).

## 5. Validation
- Command executed from `chatappBE`:
  - `./gradlew.bat :user-service:compileJava :upload-service:compileJava --continue --no-daemon`
- Result:
  - `BUILD SUCCESSFUL in 37s`
  - `13 actionable tasks: 1 executed, 12 up-to-date`
