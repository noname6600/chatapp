## Context

Spring Cloud Gateway proxies responses from downstream services. Each downstream service (auth-service, user-service, etc.) has its own CORS configuration that adds `Access-Control-Allow-Origin` to every response. The gateway's `CorsWebFilter` then adds the same header again. The browser receives two identical values in a single header and rejects the response per the CORS spec.

The previous fix (`fix-cors-duplicate-with-downstream`) added a `corsHeaderDedupeWebFilter()` WebFilter with `@Order(Ordered.LOWEST_PRECEDENCE)` using a `.then(Mono.fromRunnable(…))` callback. This is unreliable: Spring Cloud Gateway's proxy can flush the response (commit headers) before the WebFlux filter chain's `.then()` runs, so the deduplication never actually takes effect on a live request.

Spring Cloud Gateway ships `DedupeResponseHeaderGatewayFilterFactory`, a first-class routing pipeline filter that hooks into the gateway's response writing phase — not the WebFlux filter chain — ensuring it always runs before headers are committed.

## Goals / Non-Goals

**Goals:**
- Reliably remove duplicate `Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials` headers from every proxied response using the officially supported Gateway mechanism
- Eliminate the custom WebFilter dedup code that can silently fail
- Keep all existing gateway CORS logic (`CorsWebFilter`, `CorsConfigurationSource`, env-driven origins) intact

**Non-Goals:**
- Changing downstream service CORS configurations
- Replacing `CorsWebFilter` as the edge CORS authority
- Handling any other duplicate headers beyond the two CORS headers

## Decisions

### Decision 1 — Use `DedupeResponseHeader` default filter, not a custom WebFilter

**Chosen**: Add `DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST` to `spring.cloud.gateway.server.webflux.default-filters` in `application.yaml`.

**Rejected**: Keep/fix the custom `corsHeaderDedupeWebFilter()` WebFilter.

**Rationale**: `DedupeResponseHeaderGatewayFilterFactory` operates inside the routing filter chain at response-write time, guaranteed before headers are flushed. A WebFlux `WebFilter` runs in the outer servlet-equivalent chain where `.then()` callbacks execute after the write completes. The Gateway filter is also declarative config (zero Java code) and is tested by the Spring team.

### Decision 2 — Strategy `RETAIN_FIRST`

**Chosen**: `RETAIN_FIRST` — keep the first occurrence, discard later duplicates.

**Rationale**: The first header value is set by the gateway's `CorsWebFilter` which validates the `Origin` against our allow-list. The downstream value is redundant and may differ in whitespace or casing. Keeping the gateway's authoritative value is correct.

**Alternative considered**: `RETAIN_LAST` — would keep the downstream value, defeating the purpose. `RETAIN_UNIQUE` — would keep both if they differ (e.g., different casing), which can still break browsers.

### Decision 3 — Remove custom dedup WebFilter from GatewayConfig

**Chosen**: Delete `corsHeaderDedupeWebFilter()` and `dedupeHeader()` from `GatewayConfig.java`.

**Rationale**: Dead code that never reliably fired. Leaving it creates confusion and maintenance burden. The test for it in `GatewayCorsIntegrationTest` (`responseCorsHeaders_areDeduplicated`) must also be removed since it tests a removed behavior.

## Risks / Trade-offs

- **Risk**: `DedupeResponseHeader` only runs for routed requests, not for local fallback endpoints.  
  → **Mitigation**: Fallback endpoints (`/fallback/**`) do not call downstream services and therefore never see downstream CORS headers — no duplicate can exist there.

- **Risk**: If `CorsWebFilter` does not run (e.g., gateway CORS disabled) and only downstream headers exist, `RETAIN_FIRST` still keeps the single downstream value correctly — safe.

- **Trade-off**: Filter is configured in YAML, not in code — can't be unit-tested directly. Acceptance validated by smoke test (curl preflight, check single `Access-Control-Allow-Origin` header).

## Migration Plan

1. Add `DedupeResponseHeader` line to `application.yaml` default-filters
2. Remove `corsHeaderDedupeWebFilter()` and `dedupeHeader()` from `GatewayConfig.java` (remove unused imports too)
3. Update `GatewayCorsIntegrationTest`: remove `responseCorsHeaders_areDeduplicated()` test; add a config-presence assertion that the yaml default-filter string is correct
4. Run `./gradlew -p gateway-service cleanTest test bootJar -x test`
5. Smoke test: `curl -si -X OPTIONS http://localhost:8080/api/v1/auth/login -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: POST" | grep -c "Access-Control-Allow-Origin"` — expect `1`

**Rollback**: Remove the `DedupeResponseHeader` line from `application.yaml` and re-add the WebFilter bean. No data migrations involved.

## Open Questions

- None. `DedupeResponseHeaderGatewayFilterFactory` is a stable, documented Spring Cloud Gateway filter.
