# Remaining Issues And Blockers

## Unresolved Issues

- No blocking compile errors remain for validated services.
- Deprecation warnings remain in multiple test suites (MockBean, AntPathRequestMatcher, deprecated APIs).

## Blocked Fixes

- None were blocked by shared/common modifications during this pass.

## Issues Requiring Shared/Common Modifications

- Shared artifact hygiene concern observed: stale local common-web jar content caused initial classpath mismatch.
- Durable prevention may require shared build-pipeline hardening (for example stricter clean/rebuild or artifact validation).
- This was documented only; shared/common source was not modified.

## Flaky Tests / Test Reliability Risks

- No flaky test execution was diagnosed because the stabilization pass focused on compileJava and compileTestJava.
- Integration tests depending on Redis/Kafka runtime were not executed end-to-end.

## Infrastructure Problems

- Deprecated Gradle features reported on all runs (future Gradle 9 compatibility risk).

## Architecture Risks

- Requested target names differed from repository modules:
  - Requested media-service not present; upload-service exists and was used as the nearest media analog.
  - Requested edge-service not present; realtime-edge-service exists and was used.
- Current pass did not perform architecture rewrites; only localized compile/test compatibility repairs were applied.
