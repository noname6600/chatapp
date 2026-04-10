# gateway-edge-cors-coordination Specification

## Purpose
Define how the API gateway handles browser CORS at the network edge: evaluating preflight requests for routed HTTP APIs, deduplicating CORS response headers that may be added by both the gateway and downstream services, and reading allowed origins from environment-driven configuration.

## Requirements

### Requirement: Gateway SHALL answer browser CORS preflight for routed HTTP APIs
The gateway SHALL evaluate browser preflight `OPTIONS` requests for routed HTTP API paths and return CORS response headers when the request origin is allowed.

#### Scenario: Allowed origin preflight succeeds
- **WHEN** a browser sends an `OPTIONS` preflight request to a routed API path with an origin present in `CORS_ALLOWED_ORIGINS`
- **THEN** the gateway returns a successful preflight response including `Access-Control-Allow-Origin` and required CORS headers

#### Scenario: Disallowed origin preflight is not allowed
- **WHEN** a browser sends an `OPTIONS` preflight request to a routed API path with an origin not present in `CORS_ALLOWED_ORIGINS`
- **THEN** the gateway does not authorize CORS for that origin and the browser blocks the cross-origin call

### Requirement: Gateway SHALL deduplicate downstream CORS response headers
When downstream services return CORS headers, the gateway SHALL ensure proxied responses contain a single effective value for each configured CORS response header.

#### Scenario: Duplicate Access-Control-Allow-Origin is reduced to one value
- **WHEN** both gateway and downstream response pipelines provide `Access-Control-Allow-Origin`
- **THEN** the response emitted by gateway contains only one `Access-Control-Allow-Origin` header value

#### Scenario: Duplicate Access-Control-Allow-Credentials is reduced to one value
- **WHEN** both gateway and downstream response pipelines provide `Access-Control-Allow-Credentials`
- **THEN** the response emitted by gateway contains only one `Access-Control-Allow-Credentials` header value

### Requirement: Gateway CORS origins SHALL be environment-configurable
The gateway SHALL read allowed browser origins from an environment-driven configuration value and support multiple origins via comma-separated entries.

#### Scenario: Multiple origins are configured via environment
- **WHEN** `CORS_ALLOWED_ORIGINS` contains multiple comma-separated origins
- **THEN** requests from any listed origin are treated as allowed for CORS evaluation

#### Scenario: Default origin is used when environment variable is absent
- **WHEN** `CORS_ALLOWED_ORIGINS` is not set
- **THEN** the gateway uses its configured default origin value
