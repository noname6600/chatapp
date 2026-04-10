## 1. Configuration — Add Built-in DedupeResponseHeader Filter

- [x] 1.1 Add `- DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST` to `spring.cloud.gateway.server.webflux.default-filters` in `application.yaml`

## 2. Code Cleanup — Remove Custom Dedup WebFilter

- [x] 2.1 Remove `corsHeaderDedupeWebFilter()` method from `GatewayConfig.java`
- [x] 2.2 Remove `dedupeHeader()` private helper method from `GatewayConfig.java`
- [x] 2.3 Remove unused imports from `GatewayConfig.java` (`Ordered`, `Order`, `HttpHeaders`, `WebFilter`, `Mono`, `reactor.core.publisher.Mono`) — keep only what remains used

## 3. Tests — Update GatewayCorsIntegrationTest

- [x] 3.1 Remove `responseCorsHeaders_areDeduplicated()` test method (tests removed behavior)
- [x] 3.2 Verify remaining 3 tests still compile and pass: `preflight_allowsConfiguredOrigin`, `preflight_rejectsUnconfiguredOrigin`, `corsOrigins_parsesCommaSeparatedValuesAndIgnoresBlanks`

## 4. Validation

- [x] 4.1 Run `./gradlew -p gateway-service cleanTest test` — expect BUILD SUCCESSFUL
- [x] 4.2 Run `./gradlew -p gateway-service bootJar -x test` — expect BUILD SUCCESSFUL
- [x] 4.3 Smoke test: start gateway locally and verify curl OPTIONS preflight returns exactly one `Access-Control-Allow-Origin` header
