# gateway-circuit-breaker

## Purpose
Gateway capability specification for gateway-circuit-breaker.

## Requirements

### Requirement: Per-route circuit breakers protect downstream services
Each downstream service route SHALL have an independent Resilience4j circuit breaker. When a circuit opens, the gateway SHALL return a fallback response instead of forwarding requests.

#### Scenario: Circuit breaker opens after failure threshold
- **WHEN** 50% or more of calls in a sliding window of 10 fail for a given route
- **THEN** the circuit breaker SHALL open and subsequent requests to that route SHALL receive the fallback response

#### Scenario: Fallback response returned when circuit is open
- **WHEN** a route's circuit breaker is in the OPEN state
- **THEN** the gateway SHALL forward the request to `/fallback/service-unavailable` and return HTTP 503 to the client

#### Scenario: Circuit breakers are isolated per service
- **WHEN** the chat-service circuit breaker opens
- **THEN** requests to auth-service, user-service, and other routes SHALL continue to be forwarded normally

#### Scenario: Circuit enters half-open after wait duration
- **WHEN** 10 seconds have elapsed since the circuit opened
- **THEN** the circuit breaker SHALL allow up to 3 probe requests through to test service recovery

#### Scenario: Circuit closes on successful probes
- **WHEN** the allowed probe requests in half-open state succeed
- **THEN** the circuit breaker SHALL close and resume normal forwarding

### Requirement: Circuit breaker configuration
Each circuit breaker SHALL use a sliding window of 10 calls, 50% failure rate threshold, 10 second open wait duration, and 3 permitted calls in half-open state.

#### Scenario: Default config applied to all routes
- **WHEN** the gateway starts
- **THEN** all service circuit breakers (auth-cb, user-cb, chat-cb, presence-cb, friendship-cb, notification-cb, upload-cb) SHALL be configured with the default settings

