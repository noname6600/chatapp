# cors-dedup-default-filter Specification

## Purpose
Define how the Gateway deduplicates CORS response headers using Spring Cloud Gateway's built-in `DedupeResponseHeader` default filter, eliminating reliance on custom WebFilter post-processing.

## Requirements

### Requirement: Gateway deduplicates CORS response headers via built-in filter
The gateway SHALL apply Spring Cloud Gateway's `DedupeResponseHeader` default filter to every proxied route, configured to RETAIN_FIRST for `Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials`. After deduplication, each CORS response header SHALL appear at most once in the response sent to the browser.

#### Scenario: Downstream and gateway both add Access-Control-Allow-Origin
- **WHEN** a downstream service response contains `Access-Control-Allow-Origin: http://localhost:5173` AND the gateway's `CorsWebFilter` also adds `Access-Control-Allow-Origin: http://localhost:5173`
- **THEN** the response forwarded to the browser contains exactly one `Access-Control-Allow-Origin` header with value `http://localhost:5173`

#### Scenario: Downstream and gateway both add Access-Control-Allow-Credentials
- **WHEN** a downstream service response contains `Access-Control-Allow-Credentials: true` AND the gateway's `CorsWebFilter` also adds `Access-Control-Allow-Credentials: true`
- **THEN** the response forwarded to the browser contains exactly one `Access-Control-Allow-Credentials` header with value `true`

#### Scenario: Only gateway adds CORS headers (downstream has no CORS)
- **WHEN** a downstream service response does not contain `Access-Control-Allow-Origin` AND the gateway's `CorsWebFilter` adds `Access-Control-Allow-Origin: http://localhost:5173`
- **THEN** the response forwarded to the browser contains exactly one `Access-Control-Allow-Origin` header (the gateway-issued value)

### Requirement: Gateway CORS deduplication does not use custom WebFilter post-processing
The system SHALL NOT rely on a custom `WebFilter` with `.then()` post-processing to deduplicate CORS headers. The `corsHeaderDedupeWebFilter` bean and `dedupeHeader` helper SHALL be removed from `GatewayConfig`.

#### Scenario: No custom dedup WebFilter bean exists
- **WHEN** the gateway application context starts
- **THEN** no bean of type `WebFilter` named `corsHeaderDedupeWebFilter` is registered in the context
