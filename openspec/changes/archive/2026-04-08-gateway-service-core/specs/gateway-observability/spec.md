## ADDED Requirements

### Requirement: Actuator health endpoint exposed
The gateway SHALL expose a health endpoint that can be used by Docker Compose health checks and orchestration tools.

#### Scenario: Health endpoint returns UP
- **WHEN** the gateway is running and Redis is reachable
- **THEN** `GET /actuator/health` SHALL return HTTP 200 with status `UP`

#### Scenario: Health endpoint shows component details
- **WHEN** `GET /actuator/health` is called
- **THEN** the response SHALL include status details for all health components

### Requirement: Prometheus metrics endpoint exposed
The gateway SHALL expose a Prometheus-compatible metrics endpoint for scraping by a monitoring system.

#### Scenario: Metrics endpoint accessible
- **WHEN** Prometheus scrapes `GET /actuator/prometheus`
- **THEN** the gateway SHALL return metrics in Prometheus text format including request counts, latencies, and JVM metrics

#### Scenario: Per-route metrics available
- **WHEN** traffic flows through the gateway
- **THEN** Micrometer SHALL record per-route request metrics (route ID, HTTP method, status) available at the Prometheus endpoint

### Requirement: Liveness and readiness probes exposed
The gateway SHALL expose separate liveness and readiness endpoints for container orchestration.

#### Scenario: Liveness probe
- **WHEN** `GET /actuator/health/liveness` is called
- **THEN** the gateway SHALL return HTTP 200 if the application is alive

#### Scenario: Readiness probe
- **WHEN** `GET /actuator/health/readiness` is called
- **THEN** the gateway SHALL return HTTP 200 only when the gateway is ready to accept traffic
