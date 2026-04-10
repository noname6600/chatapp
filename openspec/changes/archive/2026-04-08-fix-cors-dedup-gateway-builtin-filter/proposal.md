## Why

The previous CORS deduplication fix added a WebFilter (`corsHeaderDedupeWebFilter`) that runs in Spring WebFlux's filter chain. However, once Spring Cloud Gateway proxies a response from a downstream service, the response may already be flushed before the WebFilter `.then()` callback can modify headers — so duplicate `Access-Control-Allow-Origin` headers still reach the browser. The browser rejects any response with two values in that header, breaking all cross-origin requests to every service behind the gateway.

## What Changes

- Remove the custom `corsHeaderDedupeWebFilter()` WebFilter bean and `dedupeHeader()` helper from `GatewayConfig` — they are unreliable post-response
- Add `DedupeResponseHeader` as a global default filter in `application.yaml` using Spring Cloud Gateway's built-in `DedupeResponseHeaderGatewayFilterFactory` (strategy `RETAIN_FIRST`) for `Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials`
- Update `GatewayCorsIntegrationTest` to remove the WebFilter deduplication test and add a test validating the default-filter config string is parseable / present

## Capabilities

### New Capabilities

- `cors-dedup-default-filter`: Gateway deduplicates CORS response headers via built-in `DedupeResponseHeader` default filter, applied to every proxied route before the response reaches the browser.

### Modified Capabilities

- `gateway-edge-cors-coordination`: Deduplication strategy changes from a custom WebFilter post-processing to a native Gateway GlobalFilter at routing time.

## Impact

- `chatappBE/gateway-service/src/main/java/com/example/gateway/config/GatewayConfig.java` — remove `corsHeaderDedupeWebFilter()` and `dedupeHeader()`
- `chatappBE/gateway-service/src/main/resources/application.yaml` — add `default-filters: - DedupeResponseHeader=...` under `spring.cloud.gateway`
- `chatappBE/gateway-service/src/test/java/com/example/gateway/config/GatewayCorsIntegrationTest.java` — remove flaky WebFilter test, replace with config-presence assertion
- No downstream service changes required
