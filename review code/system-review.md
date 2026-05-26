## 1. Summary

- `common-core` currently mixes foundational concerns with transport concerns. Its exception contract is treated as a domain/core abstraction, but it depends directly on Spring Web `HttpStatus`, so the module is not framework-neutral.
- `web` is a mixed shared-adapter module. It contains HTTP response wrappers, exception handling, trace propagation, CORS configuration, a base controller abstraction, and Feign client tracing in one place. That is multiple concerns under one shared bucket rather than one coherent layer.
- `security` is extremely small, but the one exported class, `com.example.common.security.jwt.JwtHelper`, is still tightly bound to Spring Security JWT types and to the shared business exception model.
- `media/upload` is split across two places with unclear ownership: `common-media` exposes `com.example.common.media.UploadAssetMetadata`, while `upload-service` owns upload policy, HTTP DTOs, signing, and confirmation logic. The shared/media boundary is weak and upload concepts leak outside the upload module.
- Overall architecture health is below clean-architecture expectations. Dependency direction is only partially correct at the Gradle level; at the code level there are several reverse conceptual dependencies, shared-module overreach, and boundary leaks.

## 2. Module Breakdown

### common-core

- Responsibilities
- Defines shared exception abstractions: `com.example.common.core.exception.IErrorCode`, `CommonErrorCode`, `BusinessException`.
- Defines generic pipeline orchestration primitives: `PipelineStep`, `PipelineExecutor`, `PipelineFactory`, `PipelineGraphResolver`, `PipelineStepDescriptor`, `StepCondition`, `StepRetryPolicy`.

- Issues
- `com.example.common.core.exception.IErrorCode` depends on `org.springframework.http.HttpStatus` in `common/common-core/src/main/java/com/example/common/core/exception/IErrorCode.java:3-7`.
- `com.example.common.core.exception.CommonErrorCode` hardcodes HTTP semantics in the supposed core layer in `common/common-core/src/main/java/com/example/common/core/exception/CommonErrorCode.java:5-24`.
- `common/common-core/build.gradle:32-35` declares `org.springframework:spring-web`, proving the module is framework-coupled despite its name.
- The module groups two unrelated concerns: exception policy and pipeline orchestration. There is no evident cohesion between them beyond “shared code”.

- Violations
- Clean architecture violation: core depends on delivery-layer semantics through `HttpStatus`.
- Separation-of-concerns violation: exception policy and workflow/pipeline engine are bundled in the same foundational module without a shared abstraction boundary.
- Misleading module naming: `common-core` is not a pure core module because it cannot exist without Spring Web.

### web

- Responsibilities
- Provides HTTP response wrappers: `com.example.common.web.response.ApiResponse`, `ApiError`.
- Provides global HTTP exception translation: `com.example.common.web.exception.GlobalExceptionHandler`.
- Provides servlet tracing filter: `com.example.common.web.filter.TraceIdFilter`.
- Provides CORS property binding: `com.example.common.web.cors.CorsProperties`.
- Provides controller inheritance helper: `com.example.common.web.controller.BaseController`.
- Provides Feign outbound trace propagation: `com.example.common.web.config.FeignTraceConfig`.

- Issues
- `com.example.common.web.exception.BusinessException` and `com.example.common.web.exception.IErrorCode` are deprecated compatibility wrappers over `common-core` in `common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java:3-20` and `common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java:3-4`. They are misplaced shadow abstractions that duplicate ownership of error contracts.
- `com.example.common.web.exception.GlobalExceptionHandler` depends directly on `com.example.common.core.exception.BusinessException` and `CommonErrorCode` in `common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java:3-4`, which means web is coupled to core exception design instead of only adapting generic failures.
- `com.example.common.web.cors.CorsProperties` has legacy fallback to the `common.security.cors` property namespace in `common/common-web/src/main/java/com/example/common/web/cors/CorsProperties.java:17,22-29`. That is cross-module configuration leakage from web into security naming.
- `com.example.common.web.config.FeignTraceConfig` is an outbound client concern, not an inbound web concern. It is misplaced in the web module.
- `common/common-web/build.gradle:39-48` pulls in Spring MVC, security-core, validation, Jackson, and OpenFeign. This module is acting as a general integration bucket, not a focused web boundary module.
- The presence of bean-producing classes (`TraceIdFilter`, `GlobalExceptionHandler`, `CorsProperties`, `FeignTraceConfig`) means importing or scanning the module auto-activates behavior, which increases hidden coupling across services.

- Violations
- Wrong abstraction ownership: duplicated exception types in web instead of a single source of truth.
- Layer mixing: HTTP API concerns, observability, outbound client tracing, and configuration binding all live in one module.
- Package-structure inconsistency: the module name says `web`, but it also contains cross-cutting infrastructure (`FeignTraceConfig`) and compatibility shims.

### security

- Responsibilities
- Provides JWT helper logic through `com.example.common.security.jwt.JwtHelper`.

- Issues
- `com.example.common.security.jwt.JwtHelper` accepts Spring Security `Jwt` directly in `common/common-security/src/main/java/com/example/common/security/jwt/JwtHelper.java:14-23`. This is a framework adapter, not a reusable domain/security abstraction.
- The same class throws `com.example.common.core.exception.BusinessException` with `CommonErrorCode.UNAUTHORIZED` in `common/common-security/src/main/java/com/example/common/security/jwt/JwtHelper.java:15-23`. Security parsing and business/application error policy are fused.
- `common/common-security` is too thin to justify a separate module in its current form. It exports one static helper but still introduces a separate project boundary.

- Violations
- Clean architecture violation: framework principal parsing is exposed as a shared “security” abstraction.
- Layer leakage: authentication-token adapter code depends on business exception policy from `common-core`.
- Structural weakness: a whole module exists for one utility class with no stronger abstraction behind it.

### media/upload

- Responsibilities
- `common-media` exposes shared upload metadata through `com.example.common.media.UploadAssetMetadata`.
- `upload-service` exposes upload endpoints through `com.example.upload.controller.UploadController`.
- `upload-service` implements policy lookup and signing/confirmation logic in `com.example.upload.service.UploadPolicyRegistry` and `UploadSigningService`.
- `upload-service` holds upload-specific domain/data types: `UploadPurpose`, `UploadPolicy`, `PrepareUploadRequest`, `PrepareUploadResponse`, `ConfirmUploadRequest`, `UploadAssetResponse`, `UploadPolicyProperties`.

- Issues
- `common-media` contains only `com.example.common.media.UploadAssetMetadata` in `common/common-media/src/main/java/com/example/common/media/UploadAssetMetadata.java:1-18`. That is not a general media module; it is an upload-specific DTO-shaped contract.
- `UploadAssetMetadata` is used by both `com.example.upload.service.UploadSigningService` and `com.example.user.service.impl.UserProfileService`, which means upload semantics are leaking into another bounded context through a shared common module instead of a stable API/client contract.
- `com.example.upload.service.UploadSigningService` directly consumes request DTOs and returns response DTOs in `upload-service/src/main/java/com/example/upload/service/UploadSigningService.java:38-121`. The service layer is coupled to the web contract instead of an application command/result model.
- The same class creates `UploadAssetMetadata` only to immediately map it into `UploadAssetResponse` in `UploadSigningService.java:97-120`. That is a weak abstraction and a sign that `UploadAssetMetadata` is misplaced or redundant.
- `com.example.upload.domain.UploadPurpose` contains Jackson `@JsonCreator` in `upload-service/src/main/java/com/example/upload/domain/UploadPurpose.java:3,19-32`. Serialization concerns are embedded in a domain enum.
- `com.example.upload.service.UploadPolicyRegistry` depends directly on `UploadPolicyProperties.Purpose` in `upload-service/src/main/java/com/example/upload/service/UploadPolicyRegistry.java:22,44-70`. Configuration schema and domain policy assembly are coupled inside the service package.
- `com.example.upload.config.SecurityConfig` rebuilds CORS handling directly from `CorsProperties` in `upload-service/src/main/java/com/example/upload/config/SecurityConfig.java:24-85`. Shared web config is not abstracted; each service owns duplicated security/CORS assembly logic.
- `com.example.upload.UploadServiceApplication` broad-scans `com.example.common` in `upload-service/src/main/java/com/example/upload/UploadServiceApplication.java:9-11`, which makes shared beans from common modules implicitly active and weakens explicit module boundaries.
- `upload-service/build.gradle:27-37` depends on `common-web`, `common-core`, and `common-media`, but not on `common-security`, even though it is a secured resource server. Security concerns are therefore partly shared and partly rebuilt locally.

- Violations
- Business logic misplaced: application/service logic depends on transport DTOs in `UploadSigningService`.
- Structural issue: upload concepts are split between `common-media` and `upload-service` without a clean ownership rule.
- Layer leakage: domain enum `UploadPurpose` carries JSON binding concerns.
- Missing boundary: there is no dedicated upload application boundary or client contract module; shared model usage is ad hoc.

## 3. Problems

- High
- `common-core` is not core because `IErrorCode` and `CommonErrorCode` depend on `HttpStatus`, and `common-core` depends on `spring-web`.
- `common-web` keeps duplicate deprecated exception contracts (`com.example.common.web.exception.BusinessException`, `IErrorCode`) that overlap with `common-core` and create dual ownership.
- `UploadSigningService` is a layer-violating service that depends on inbound/outbound DTOs directly and also uses shared `UploadAssetMetadata` as a transient mapping object.
- `UploadPurpose` embeds Jackson binding in the domain package.

- Medium
- `CorsProperties` leaks legacy `common.security.cors` namespace into the web module.
- `FeignTraceConfig` is misplaced in the web module and broadens the module into a generic infrastructure bucket.
- `JwtHelper` fuses Spring JWT parsing with business exception policy.
- `UploadPolicyRegistry` mixes configuration binding, normalization, validation, and domain object construction in one component.

- Low
- `common-security` is too small and utility-like for its own module.
- `BaseController` adds inheritance-based coupling for response wrapping, though this is less severe than the other issues.
- `common-media` name is inconsistent with its actual contents and misrepresents ownership.

## 4. Violations

- Wrong layer dependencies
- `com.example.common.core.exception.IErrorCode` and `CommonErrorCode` depend on HTTP status types.
- `com.example.common.security.jwt.JwtHelper` depends on Spring Security `Jwt` and on `common-core` business exceptions.
- `com.example.upload.service.UploadSigningService` depends on web DTOs instead of application commands/results.
- `com.example.upload.domain.UploadPurpose` depends on Jackson annotations.

- Business logic misplaced
- `UploadSigningService` holds validation and confirmation policy but is expressed in terms of controller DTOs.
- `UploadPolicyRegistry` performs configuration validation and domain construction inside the `service` package rather than a configuration or policy assembly boundary.
- `GlobalExceptionHandler` centralizes response shaping correctly for HTTP, but the deprecated web exception wrappers around it should not still exist.

- Structural issues
- `common-web` is a grab-bag module, not a single-layer module.
- `common-media` is effectively a single upload DTO module with an inaccurate name.
- `upload-service` lacks explicit application/domain/infrastructure boundaries and uses flat `config/controller/domain/dto/service` packaging.
- Broad `@ComponentScan("com.example.common")` weakens modular explicitness.

## 5. Dependency Direction

- Validate correct dependency flow
- Gradle-level flow is mostly one-way: `common-web -> common-core`, `common-security -> common-core`, and `upload-service -> common-web/common-core/common-media`.
- `upload-service` does not introduce a compile-time reverse dependency into the common modules.

- Detect reverse dependencies
- There is a conceptual reverse dependency from `common-core` to the web layer because HTTP status mapping is embedded in `IErrorCode` and `CommonErrorCode`.
- `CorsProperties` introduces semantic reverse coupling from web into security through the legacy `common.security.cors` namespace.
- `common-media.UploadAssetMetadata` creates cross-service coupling between `upload-service` and `user-service`, turning a shared module into an inter-service data bridge.
- `UploadServiceApplication` reverse-couples runtime composition by scanning `com.example.common` wholesale rather than importing explicit configurations.

## 6. Architecture Issues

- Tight coupling
- `UploadSigningService` is tightly coupled to Cloudinary configuration, upload policy lookup, transport DTOs, and shared metadata representation.
- `JwtHelper` is tightly coupled to Spring Security JWT representation.
- `GlobalExceptionHandler` is tightly coupled to the exact shape of the core exception contract.

- Layer leakage
- HTTP status leaks into core.
- Jackson leaks into upload domain.
- Security token parsing leaks into controller/application error policy.
- Feign client tracing leaks into the web shared module.

- Duplicate logic
- Error-contract ownership is duplicated between `common-core` and deprecated types in `common-web`.
- Upload metadata validation rules are not centralized at the upload boundary because `UploadAssetMetadata` is reused outside the upload service.
- CORS/security assembly appears service-local even though the property object is shared.

- Missing abstractions
- No transport-neutral error abstraction in `common-core`.
- No dedicated upload application model separate from REST DTOs.
- No explicit client contract module for upload confirmation/asset metadata.
- No explicit auto-configuration boundary for shared web concerns; behavior is activated by package scanning instead.

## 7. Recommendations

- Refactor suggestions
- Move HTTP status mapping out of `common-core`. `IErrorCode` should be framework-neutral, and web-specific status translation should live in `common-web`.
- Delete `com.example.common.web.exception.BusinessException` and `com.example.common.web.exception.IErrorCode` after migration, and keep error-contract ownership only in `common-core`.
- Split `common-web` by concern or convert it into explicit opt-in auto-configuration modules. At minimum separate HTTP API concerns from Feign tracing and from tracing/filter infrastructure.
- Replace `JwtHelper.extractUserId(Jwt)` with either:
  - a web/security adapter local to each service controller layer, or
  - a narrower shared adapter whose responsibility is only JWT claim extraction and not business exception creation.
- Collapse `common-media` into one of two clearer options:
  - move `UploadAssetMetadata` into `upload-service` if it is upload-owned, or
  - create a dedicated upload contract/client module if multiple services truly share the contract.
- Introduce an application boundary inside `upload-service`. `UploadSigningService` should operate on application commands/results, while controllers map REST DTOs to those commands/results.
- Move Jackson annotations off `UploadPurpose` and keep serialization adapters in DTOs or converters.
- Move upload policy assembly out of `UploadPolicyRegistry` service logic into a configuration/factory boundary.
- Replace broad component scanning with explicit imports/configuration registration for shared modules.

- What to move/split/remove
- Move HTTP status handling from `common-core` to `common-web`.
- Remove deprecated web exception wrappers.
- Move `FeignTraceConfig` out of `common-web` into a dedicated integration/observability module.
- Move `UploadAssetMetadata` out of `common-media` unless a real cross-service upload contract is intentionally being published.
- Split upload responsibilities into controller adapter, application service, policy domain, and infrastructure signing/client integration.
