# Phase 3 - Gateway Readiness Tightening

Date: 2026-05-14
Execution source of truth: review code/service-fix-plan.md
Scope: gateway-service only

## Objective
Tighten downstream readiness semantics so arbitrary 4xx responses are not treated as healthy.

## Implemented Changes

1. Tightened readiness acceptance policy in DownstreamReadinessIndicator
- Updated downstream probe success criteria from broad 2xx-4xx acceptance to strict 2xx-only acceptance.
- This prevents auth/routing failures (for example 401/403/404) from being reported as healthy.

2. Kept behavior operationally practical and minimal
- Retained existing health-path probing model through gateway.readiness.health-path (default /actuator/health).
- Kept timeout/error handling and overall readiness aggregation behavior unchanged.
- No websocket cutover or routing architecture changes were introduced.

3. Added focused test coverage
- Added unit test that verifies a client-error response (401) from a required downstream causes readiness DOWN.
- Existing UP/DOWN baseline tests remain intact.

## Files Changed
- chatappBE/gateway-service/src/main/java/com/example/gateway/health/DownstreamReadinessIndicator.java
- chatappBE/gateway-service/src/test/java/com/example/gateway/health/DownstreamReadinessIndicatorTest.java

## Verification Gate
Most relevant service-local gate for affected scope:
- Command: ./gradlew :gateway-service:test
- Result: FAILED (broader gateway integration test context dependency errors: NoSuchBeanDefinition/UnsatisfiedDependencyException)

Focused readiness gate for changed component:
- Command: ./gradlew :gateway-service:test --tests "com.example.gateway.health.DownstreamReadinessIndicatorTest"
- Result: BUILD SUCCESSFUL

## Safety Notes
- Change is narrow and local to gateway-service.
- Readiness now better reflects real downstream availability and avoids masking auth/routing issues as healthy.
