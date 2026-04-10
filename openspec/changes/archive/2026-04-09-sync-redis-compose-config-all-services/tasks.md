## 1. Compose Redis Contract Synchronization

- [x] 1.1 Inventory Redis-dependent services in [chatappBE/docker-compose.local.yml](chatappBE/docker-compose.local.yml) and define the canonical Redis env contract.
- [x] 1.2 Add or normalize `SPRING_DATA_REDIS_HOST=redis` and `SPRING_DATA_REDIS_PORT=6379` for each Redis-dependent service.
- [x] 1.3 Add or normalize Redis health-gated `depends_on` entries for each Redis-dependent service.

## 2. Cross-Service Consistency Validation

- [x] 2.1 Compare Redis configuration conventions across services to ensure no service retains localhost fallback defaults in compose runtime env.
- [x] 2.2 Validate compose configuration syntax and resolved environment values before restart.

## 3. Full Stack Restart and Runtime Verification

- [x] 3.1 Restart the full local compose stack using [chatappBE/docker-compose.local.yml](chatappBE/docker-compose.local.yml).
- [x] 3.2 Verify Redis service health and per-service startup stability after restart.
- [x] 3.3 Inspect logs of Redis-dependent services to confirm absence of `localhost:6379` Redis connection attempts.
- [x] 3.4 Execute targeted smoke checks for Redis-dependent flows (including user profile self endpoint) to verify runtime connectivity.
