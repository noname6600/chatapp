## 1. Summary

### common-core
* What it contains
  * `chatappBE/common/common-core` currently contains only framework-neutral pipeline utilities: `PipelineStep`, `PipelineStepDescriptor`, `PipelineGraphResolver`, `PipelineExecutor`, `PipelineFactory`, `StepCondition`, and `StepRetryPolicy`.
* Whether it is truly generic
  * At code level, yes. The Java classes are generic and do not depend on Spring, HTTP, security, domain modules, or transport modules.
  * At build level, it is mostly generic, but the module still imports the Spring Boot BOM in [`chatappBE/common/common-core/build.gradle`](../chatappBE/common/common-core/build.gradle). That does not create a code dependency, but it is unnecessary framework affinity for a supposedly framework-agnostic core module.

### common-web
* What it contains
  * Shared HTTP/web infrastructure: `BaseController`, `ApiResponse`, `ApiError`, `GlobalExceptionHandler`, `TraceIdFilter`, `FeignTraceConfig`, and `CorsProperties`.
  * It also contains security/auth-related helpers and contracts: `JwtHelper`, `BusinessException`, `ErrorCode`, and `IErrorCode`.
* Whether it is reusable HTTP/web infrastructure
  * Partially. The filter, exception advice, response wrappers, CORS properties, and Feign trace propagation are reusable web infrastructure.
  * The module is not strictly HTTP/web-only because it also acts as a shared security/auth helper module and as a cross-layer business exception foundation used outside controllers.

---

## 2. Problems

### High
* `BusinessException` is in `common-web` but is not web-specific. It is a generic application/business exception contract and is being used broadly by service and domain code across the codebase. That makes `common-web` a higher-level shared business dependency instead of a pure web adapter module.
  * Files: `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java`, `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java`
* `JwtHelper` is misplaced in `common-web`. It depends directly on `org.springframework.security.oauth2.jwt.Jwt` and performs authenticated principal extraction, which is security/auth logic rather than generic HTTP/web infrastructure.
  * File: `chatappBE/common/common-web/src/main/java/com/example/common/web/security/JwtHelper.java`

### Medium
* `common-web` has a direct security stack dependency through `spring-boot-starter-oauth2-resource-server`, even though only `JwtHelper` and the auth branches in `GlobalExceptionHandler` appear to need security types. This broadens the module beyond reusable web infrastructure.
  * File: `chatappBE/common/common-web/build.gradle`
* `ErrorCode` mixes generic web error mapping with auth/security-specific outcomes (`UNAUTHORIZED`, `FORBIDDEN`, `PERMISSION_DENIED`). This is acceptable for HTTP response mapping, but because the enum sits beside `BusinessException` and is reused outside the web edge, it reinforces the boundary leak.
  * File: `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
* `CorsProperties` is web-related, but its configuration prefix is `common.security`, which is package/domain naming drift. CORS is browser HTTP policy, not authentication logic.
  * File: `chatappBE/common/common-web/src/main/java/com/example/common/web/cors/CorsProperties.java`

### Low
* `common-core` still carries Spring dependency management in Gradle even though the code is plain Java. For a module meant to stay generic, that is unnecessary coupling at the build boundary.
  * File: `chatappBE/common/common-core/build.gradle`
* Package naming is inconsistent in `common-web`: `controller`, `response`, `filter`, `config`, `cors`, `exception`, and `security` are reasonable, but `IErrorCode` uses an `I` prefix while the rest of the codebase is class-style naming. This is minor, but it adds noise in a shared module.
  * File: `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java`

---

## 3. Violations

### Files/classes that depend on domain logic
* None found inside `common-core`.
* None found inside `common-web`.
* There are no direct references in these modules to user/auth/chat/message/friendship/notification/presence/upload service packages.

### Files/classes that depend on transport logic (kafka/redis/websocket)
* None found inside `common-core`.
* None found inside `common-web`.
* `FeignTraceConfig` is HTTP client infrastructure, not a non-web transport violation.

### Files/classes that depend on security/auth logic
* `chatappBE/common/common-web/src/main/java/com/example/common/web/security/JwtHelper.java`
  * Direct dependency on `org.springframework.security.oauth2.jwt.Jwt`
  * Performs authenticated user identity extraction
* `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java`
  * Depends on `AccessDeniedException` and `AuthenticationException`
  * This is edge/web handling and can stay if `common-web` is allowed to own HTTP translation of security errors
* `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  * Includes auth/security-oriented response codes
* `chatappBE/common/common-web/src/main/java/com/example/common/web/cors/CorsProperties.java`
  * Not auth logic itself, but it is named and configured under `security`, which is misleading

### Files/classes that are misplaced between core and web
* Should not be in `common-web`:
  * `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java`
  * `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java`
  * Reason: these are generic application/business abstractions, not HTTP/web-only abstractions
* Should likely move out of `common-web` to a dedicated security module:
  * `chatappBE/common/common-web/src/main/java/com/example/common/web/security/JwtHelper.java`
  * Reason: explicit Spring Security/OAuth2 JWT coupling
* No classes in `common-core` appear misplaced into `common-web`, and no `common-web` code currently leaks into `common-core`.

---

## 4. Dependency Direction

### common-core
* Does it depend on higher-level modules?
  * No.
* Should it?
  * No. `common-core` should remain lower-level and framework-neutral.
* Additional note
  * The only questionable dependency direction is build-level Spring BOM management in `build.gradle`, but the Java code itself does not depend on higher-level modules.

### common-web
* Does it depend on service modules?
  * No direct dependency on service modules was found.
* Does it depend on core correctly?
  * It does not currently depend on `common-core`, which is fine.
* Should it?
  * It may depend on `common-core` if it ever needs generic abstractions, but it should not depend on service/domain modules.
* Boundary assessment
  * The bigger issue is the reverse conceptual leak: `common-web` currently contains abstractions that are generic enough to be used by service/domain code, which weakens its role as a pure web adapter module.

---

## 5. What should stay

### common-core
* `chatappBE/common/common-core/src/main/java/com/example/common/core/pipeline/PipelineStep.java`
* `chatappBE/common/common-core/src/main/java/com/example/common/core/pipeline/PipelineStepDescriptor.java`
* `chatappBE/common/common-core/src/main/java/com/example/common/core/pipeline/PipelineGraphResolver.java`
* `chatappBE/common/common-core/src/main/java/com/example/common/core/pipeline/PipelineExecutor.java`
* `chatappBE/common/common-core/src/main/java/com/example/common/core/pipeline/PipelineFactory.java`
* `chatappBE/common/common-core/src/main/java/com/example/common/core/pipeline/StepCondition.java`
* `chatappBE/common/common-core/src/main/java/com/example/common/core/pipeline/StepRetryPolicy.java`

### common-web
* `chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java`
* `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiResponse.java`
* `chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiError.java`
* `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java`
* `chatappBE/common/common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java`
* `chatappBE/common/common-web/src/main/java/com/example/common/web/config/FeignTraceConfig.java`
* `chatappBE/common/common-web/src/main/java/com/example/common/web/cors/CorsProperties.java`
* `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  * Keep only if this module is the HTTP status mapping layer for shared errors

---

## 6. What should move out

* `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java` -> move to a framework-neutral shared module such as `common-core` or a small dedicated `common-errors`/`common-application` module
* `chatappBE/common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java` -> move with `BusinessException` to the same framework-neutral shared module
* `chatappBE/common/common-web/src/main/java/com/example/common/web/security/JwtHelper.java` -> move to a dedicated security-focused module such as `common-security`

---

## 7. Suggested target structure

### common-core
* `com.example.common.core.pipeline`
  * `PipelineStep`
  * `PipelineStepDescriptor`
  * `PipelineGraphResolver`
  * `PipelineExecutor`
  * `PipelineFactory`
  * `StepCondition`
  * `StepRetryPolicy`
* Optional shared neutral abstractions, if you want to keep them shared and not web-bound:
  * `com.example.common.core.error`
  * or keep them outside `common-core` in a separate tiny shared module if you want `common-core` to stay pipeline-only

### common-web
* `com.example.common.web.controller`
  * `BaseController`
* `com.example.common.web.response`
  * `ApiResponse`
  * `ApiError`
* `com.example.common.web.exception`
  * `GlobalExceptionHandler`
  * `ErrorCode` only if it remains strictly about HTTP status mapping
* `com.example.common.web.filter`
  * `TraceIdFilter`
* `com.example.common.web.client` or `com.example.common.web.feign`
  * `FeignTraceConfig`
* `com.example.common.web.cors`
  * `CorsProperties`
* Avoid a `security` package in `common-web` unless the module is intentionally allowed to own security-web adapter code

---

## 8. Safe refactor steps

* Step 1: Freeze the public API of `common-core` pipeline classes and leave their behavior unchanged.
* Step 2: Introduce a new framework-neutral home for `BusinessException` and `IErrorCode` without deleting the current classes yet.
* Step 3: Update service/domain/application code to depend on the new neutral package first, while keeping `GlobalExceptionHandler` able to translate those exceptions to `ApiResponse`.
* Step 4: Move `JwtHelper` into a dedicated security-oriented shared module and keep a temporary compatibility wrapper if needed.
* Step 5: Narrow `common-web` dependencies by removing security-specific starter requirements once `JwtHelper` no longer lives there.
* Step 6: Rename or rebind `CorsProperties` from `common.security` to a web-oriented namespace only after consumers support both property names during a transition period.
* Step 7: Remove deprecated compatibility wrappers only after all consuming modules are switched.
* Step 8: After the moves, keep `common-web` limited to HTTP request/response concerns, filters/interceptors, exception-to-HTTP translation, and web configuration helpers.
