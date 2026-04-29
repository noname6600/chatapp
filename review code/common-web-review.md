## 1. Summary

- `common-web` currently contains shared HTTP/web infrastructure:
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/config/FeignTraceConfig.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiError.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiResponse.java`
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/security/CorsProperties.java`
- The module is mostly reusable HTTP/web infrastructure. It provides response wrappers, exception mapping, request tracing, Feign propagation, and shared CORS properties across services.
- It is not purely generic because some shared types embed security/auth and feature-specific semantics:
  - JWT subject parsing in `BaseController`
  - auth-token and attachment/upload-oriented constants in `ErrorCode`
- No direct domain-service dependency or non-web transport dependency was found inside `common-web` source.

## 2. Problems

### High

- No high-severity dependency-direction violation was found inside `common-web`.

### Medium

- `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java:28-37` contains JWT-specific identity extraction.
  - This is security/auth behavior, not generic controller infrastructure.
  - It assumes the authenticated principal is a `Jwt` and that `sub` is a UUID.
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java:10-28` mixes generic web/application errors with feature-specific auth and attachment semantics.
  - Auth-specific: `TOKEN_EXPIRED`, `TOKEN_INVALID`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REVOKED`
  - Upload/media-specific: `ATTACHMENT_TOO_LARGE`, `TOO_MANY_ATTACHMENTS`, `UNSUPPORTED_ATTACHMENT_TYPE`, `ATTACHMENT_INVALID`
  - This makes the shared enum broader than common-web's transport boundary.
- `chatappBE/common/common-web/build.gradle:42` depends on `spring-boot-starter-oauth2-resource-server` only because `BaseController` imports `Jwt`.
  - That security dependency is heavier than the rest of the module needs.

### Low

- `chatappBE/common/common-web/build.gradle:43` includes Jackson databind, but the module does not currently own shared `ObjectMapper` configuration.
- `chatappBE/common/common-web/src/main/java/com/example/common/web/security/CorsProperties.java` is configuration-only but lives under `security`; `config` or `cors` would be a more precise package name.
- `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiResponse.java` uses broad `lombok.*` import style and mixes generated boilerplate with manual factory methods. This is not a boundary issue, just a minor consistency issue.

## 3. Violations

### Depend on domain logic

- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - Class: `com.example.common.web.exception.ErrorCode`
  - Why: contains upload/media-specific error codes and auth-refresh-token lifecycle codes

### Depend on non-web transport logic such as kafka/redis/websocket

- None found in `common-web` source files.
- No `common-web` source file imports Kafka, Redis, or WebSocket classes.

### Depend on security/auth logic

- `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
  - Class: `com.example.common.web.controller.BaseController`
  - Why: directly depends on `org.springframework.security.oauth2.jwt.Jwt` and enforces JWT-subject-to-UUID conversion
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - Class: `com.example.common.web.exception.ErrorCode`
  - Why: contains token and refresh-token error semantics
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java`
  - Class: `com.example.common.web.exception.GlobalExceptionHandler`
  - Why: handles `AuthenticationException` and `AccessDeniedException`
  - This one is acceptable in `common-web` because it is still web/security exception mapping, not domain logic

### Are misplaced

- `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
  - JWT extraction logic is misplaced inside a generic controller base
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - Feature-specific auth/upload constants are broader than common-web should own

## 4. Dependency Direction

- Does common-web depend on higher-level modules?
  - No. `chatappBE/common/common-web/build.gradle` does not depend on service modules or higher-level common modules like events/websocket/kafka/redis.
- Does it depend on service modules?
  - No. No source file in `common-web` imports auth, user, chat, friendship, notification, or upload service classes.
- Should it?
  - No. `common-web` should remain a shared infrastructure module depended on by services, not the other way around.
  - Its dependencies should stay limited to web, validation, tracing, and narrowly-scoped shared security/web concerns.

## 5. What should stay

- `chatappBE/common/common-web/src/main/java/com/example/common/web/config/FeignTraceConfig.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiError.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiResponse.java`
- `chatappBE/common/common-web/src/main/java/com/example/common/web/security/CorsProperties.java`

These classes are correctly placed as shared web-layer infrastructure.

## 6. What should move out

- `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
  - Keep response helper methods in `common-web`
  - Move JWT/current-user resolution to `common-security` or another shared security-focused package
- `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - Move token/refresh-token-specific constants to `common-security` or auth-shared code
  - Move attachment/upload/media-specific constants to a shared media/upload package if they truly need to be shared

## 7. Suggested target structure

```text
common-web
\-- src/main/java/com/example/common/web
    +-- config
    |   \-- FeignTraceConfig.java
    +-- controller
    |   \-- BaseController.java
    +-- cors
    |   \-- CorsProperties.java
    +-- exception
    |   +-- BusinessException.java
    |   +-- GlobalExceptionHandler.java
    |   \-- IErrorCode.java
    +-- filter
    |   \-- TraceIdFilter.java
    \-- response
        +-- ApiError.java
        \-- ApiResponse.java
```

- Keep `common-web` focused on HTTP concerns: filters, response wrappers, exception mapping, web config, and validation/web error models.
- Keep JWT parsing and token lifecycle concerns outside this module.
- Keep feature-specific error codes outside this module unless they are truly generic across all web-facing services.

## 8. Safe refactor steps

1. Keep all existing HTTP response wrappers, filter behavior, and exception mapping behavior unchanged.
2. Introduce a shared security helper or resolver outside `common-web` for current-user extraction from authenticated principals.
3. Update `BaseController` to delegate JWT subject parsing to that helper without changing controller method signatures yet.
4. Split `ErrorCode` constants into:
   - transport/common application codes that stay in `common-web`
   - auth-specific codes that move to shared security/auth code
   - upload/media-specific codes that move to shared media/upload code if needed
5. Update consuming services to import the moved enums/constants gradually while keeping `BusinessException` and `IErrorCode` stable.
6. After all consumers are updated, trim `common-web` dependencies so only the web/security libraries required by the remaining classes stay in the module.
7. Optionally rename `security/CorsProperties` to a more precise package such as `cors` or `config` once imports are updated.
