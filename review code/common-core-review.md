## 1. Summary

- `common-web` currently contains shared HTTP/web-layer concerns, not core-generic utilities:
  - web config: `chatappBE/common/common-web/src/main/java/com/example/common/web/config/FeignTraceConfig.java`
  - controller base class: `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
  - exception model and handler: `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/*`
  - servlet filter: `chatappBE/common/common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java`
  - API envelope DTOs: `chatappBE/common/common-web/src/main/java/com/example/common/web/response/*`
  - security properties: `chatappBE/common/common-web/src/main/java/com/example/common/web/security/CorsProperties.java`
- It does not contain pipeline utilities, shared Jackson/ObjectMapper configuration, generic helpers, or upload metadata.
- `common-web` is not a truly generic/core module, and it should not be evaluated as one. It is intentionally coupled to web transport and security frameworks.
- As a shared web module, it is mostly well-scoped, but a few classes and enums mix in auth/upload-specific semantics that are broader than generic web infrastructure.

## 2. Problems

### High

- `common-web` is transport-coupled by design, so it should not be treated as a candidate replacement for `common-core`. Its dependencies explicitly anchor it to HTTP/security/Feign infrastructure:
  - `chatappBE/common/common-web/build.gradle:39-46`
  - This is acceptable for a `common-web` module, but it means the module is not generic.

### Medium

- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java:10-28` contains auth-token and attachment/upload-specific error codes:
  - `TOKEN_EXPIRED`, `TOKEN_INVALID`
  - `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REVOKED`
  - `ATTACHMENT_TOO_LARGE`, `TOO_MANY_ATTACHMENTS`, `UNSUPPORTED_ATTACHMENT_TYPE`, `ATTACHMENT_INVALID`
  These are not purely web-transport concerns; they encode domain/application policy in a shared web enum.
- `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java:28-37` hardcodes JWT-subject parsing into the shared controller base. That couples a generic controller helper to one authentication mechanism and one user-id representation.
- `chatappBE/common/common-web/build.gradle:43` declares Jackson databind, but the module does not provide shared `ObjectMapper` configuration. If Jackson is meant to be a shared concern here, that responsibility is currently missing.

### Low

- `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiResponse.java` uses Lombok plus manual factory methods and imports `lombok.*` broadly. This is not a boundary violation, but it is looser than the rest of the package structure.
- `chatappBE/common/common-web/src/main/java/com/example/common/web/security/CorsProperties.java` sits under `security`, but it only models configuration properties. The naming is acceptable, though `config` or `cors` would be more precise.
- The requested scope items `pipeline`, `upload metadata`, and `generic helpers` do not exist in `common-web`, so the module name and review template do not fully match the module's actual responsibility.

## 3. Violations

### Depend on domain logic

- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - Class: `com.example.common.web.exception.ErrorCode`
  - Why: contains auth-refresh-token and attachment-specific application semantics, not just transport/web concerns
- `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
  - Class: `com.example.common.web.controller.BaseController`
  - Why: assumes JWT subject contains a UUID user id, which is application/auth-policy logic rather than a generic controller abstraction

### Depend on transport (websocket/kafka/redis)

- No `common-web` source class depends on websocket, kafka, or redis.
- `common-web` does depend directly on HTTP/security/Feign transport frameworks, which is expected for this module:
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/config/FeignTraceConfig.java`
    - Feign transport coupling
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java`
    - servlet HTTP filter coupling
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java`
    - Spring MVC + Spring Security exception handling coupling
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
    - Spring MVC + JWT coupling
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/security/CorsProperties.java`
    - Spring Boot configuration coupling

### Misplaced

- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - Better fit: split shared transport-level codes from auth/upload-specific codes, or move specific codes closer to the owning module/domain
- `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
  - Better fit: keep controller response helpers in `common-web`, but move JWT-to-user-id extraction to a security/auth support type

## 4. Dependency Direction

- Does `common-web` depend on higher-level modules?
  - No project dependency on higher-level app modules was found in `chatappBE/common/common-web/build.gradle`.
  - No source file imports `common-events`, websocket, kafka, redis, or service-specific classes.
- Should it?
  - No. `common-web` should stay a shared infrastructure module, not depend on service/domain modules.
  - Its current dependency direction is acceptable for a transport-layer shared module.

## 5. What should stay

- `chatappBE/common/common-web/src/main/java/com/example/common/web/config/FeignTraceConfig.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiError.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiResponse.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/security/CorsProperties.java`

These are all reasonable shared web-layer abstractions as long as they stay transport-focused.

## 6. What should move out

- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - Move auth-refresh-token-specific and attachment-specific codes out to the owning shared auth/media/domain package, or split the enum into transport-generic vs feature-specific sets
- `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
  - Keep response helpers here, but move JWT subject parsing to a shared auth/security helper under a security-focused package

## 7. Suggested target structure

```text
common-web
\-- src/main/java/com/example/common/web
    +-- config
    |   \-- FeignTraceConfig.java
    +-- controller
    |   \-- BaseController.java
    +-- exception
    |   +-- BusinessException.java
    |   +-- GlobalExceptionHandler.java
    |   \-- IErrorCode.java
    +-- filter
    |   \-- TraceIdFilter.java
    +-- response
    |   +-- ApiError.java
    |   \-- ApiResponse.java
    +-- security
    |   \-- CorsProperties.java
    \-- securitysupport
        \-- CurrentUserResolver.java
```

- If `ErrorCode` remains shared, keep only transport-generic/common application codes in `common-web`.
- Do not add pipeline or upload packages here; those concerns belong in other shared modules.

## 8. Safe refactor steps

1. Keep `common-web` positioned as a shared transport/web module, not as a generic core module.
2. Split `ErrorCode` responsibilities by first identifying which constants are truly shared web/application codes versus auth/upload-specific codes.
3. Introduce a new shared auth/security helper for JWT subject extraction without changing controller behavior.
4. Update `BaseController` to delegate user-id extraction to that helper, preserving the current public API.
5. If desired, add a shared Jackson config in the correct shared module that is meant to own serialization defaults; do not force that responsibility into `common-web` unless the intent is web-specific serialization behavior.
6. Keep Feign trace propagation, exception handling, API envelopes, and trace filter behavior in `common-web`.
7. After responsibilities are split, clean up package names so `security` contains security concerns and configuration-only types are named precisely.
