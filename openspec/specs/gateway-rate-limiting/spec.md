# gateway-rate-limiting

## Purpose
Gateway capability specification for gateway-rate-limiting.

## Requirements

### Requirement: Per-IP request rate limiting on all routes
The gateway SHALL apply a token-bucket rate limiter to all routes, keyed by client IP address, backed by Redis. Requests exceeding the limit SHALL be rejected with HTTP 429.

#### Scenario: Request within rate limit passes
- **WHEN** a client IP sends requests within the configured replenish rate
- **THEN** the gateway SHALL forward the requests to the downstream service

#### Scenario: Request exceeding burst capacity rejected
- **WHEN** a client IP exceeds the configured burst capacity in a short window
- **THEN** the gateway SHALL return HTTP 429 Too Many Requests

#### Scenario: Rate limit state stored in Redis
- **WHEN** rate limiting is active
- **THEN** the token bucket state SHALL be stored in Redis so that it persists across gateway restarts

### Requirement: Rate limiter degrades gracefully when Redis is unavailable
If the Redis backend is unreachable, the rate limiter SHALL fail open and allow traffic through rather than denying all requests.

#### Scenario: Redis down — traffic allowed
- **WHEN** Redis is unreachable
- **THEN** the gateway SHALL forward requests without rate limiting and SHALL NOT return 429 due to Redis failure

### Requirement: Default rate limit configuration
The gateway SHALL be configured with a replenish rate of 20 requests per second per IP and a burst capacity of 40 requests.

#### Scenario: Burst capacity allows short spikes
- **WHEN** a client sends up to 40 requests in a short burst
- **THEN** all 40 requests SHALL be forwarded without rejection

