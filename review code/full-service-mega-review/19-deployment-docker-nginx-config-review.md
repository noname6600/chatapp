# Deployment, Docker & Nginx Configuration Review

**Focus**: docker-compose, environment variables, healthchecks, service dependencies, Nginx config  

---

## Docker Compose Service Configuration

### Service Startup Order

**Expected**:
```yaml
services:
  postgres:
    # Database - must start first
  redis:
    # Cache - can start early
  kafka:
    # Event streaming - needs Zookeeper
  zookeeper:
    # Kafka dependency
  gateway:
    depends_on:
      - auth
      - user
      - chat
  auth:
    depends_on:
      postgres
      kafka
  # ... other services
```

**Verification**: Check `docker-compose.yml` for proper `depends_on` ordering

---

## Environment Variables

### Gateway Service
```yaml
gateway:
  environment:
    SERVER_PORT: 8080
    SPRING_CLOUD_GATEWAY_ROUTES_0_ID: auth-service
    SPRING_CLOUD_GATEWAY_ROUTES_0_URI: http://auth-service:8081
    # ... all routes defined
```

**Verification**: All services have required env vars

---

### Auth Service
```yaml
auth:
  environment:
    SERVER_PORT: 8081
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/auth_db
    SPRING_DATASOURCE_USERNAME: postgres
    SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
    JWT_SECRET: ${JWT_SECRET}
    JWT_EXPIRATION_SECONDS: 3600
    JWT_REFRESH_EXPIRATION_SECONDS: 604800
    RESEND_API_KEY: ${RESEND_API_KEY}
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

**Issue**: Check for hardcoded values that should be env vars

---

### 🔴 BLOCKER: Upload Service Missing Cloudinary Config

**Add to upload-service environment**:
```yaml
upload:
  environment:
    CLOUDINARY_CLOUD_NAME: ${CLOUDINARY_CLOUD_NAME}
    CLOUDINARY_API_KEY: ${CLOUDINARY_API_KEY}
    CLOUDINARY_API_SECRET: ${CLOUDINARY_API_SECRET}
```

---

## Healthchecks

### 🟡 MEDIUM: Healthcheck Configuration

**Expected**:
```yaml
gateway:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 40s
```

**Issues**:
- Some services may not have healthchecks
- Timeout may be too short (services slow to start)
- start_period may not account for DB migrations

**Verification**: All services have proper healthchecks

---

## Network Configuration

### Service Discovery

**Current**: Services reference each other by hostname (e.g., `http://auth-service:8081`)

**Verification**: Docker Compose network should allow this automatically

```yaml
networks:
  default:  # Implicit bridge network
    # Services can reference each other by name
```

---

## Database Configuration

### 🟠 HIGH: Postgres Initial Setup

**Expected**:
```yaml
postgres:
  environment:
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    POSTGRES_INITDB_ARGS: --encoding=UTF8 --locale=en_US.UTF-8
  volumes:
    - postgres_data:/var/lib/postgresql/data
    - ./scripts/init-databases.sql:/docker-entrypoint-initdb.d/init.sql
```

**Missing?**: 
- Are databases created on startup? (auth_db, chat_db, etc.)
- Are migrations run automatically?

**Verification**: Check if Flyway/Liquibase is configured

---

## Kafka Configuration

### 🟡 MEDIUM: Broker Configuration

**Expected**:
```yaml
kafka:
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1  # Single broker
```

**Verification**: Kafka is properly configured for single-broker setup

---

## Ports Exposure

| Service | Port | Exposed | Purpose |
|---------|------|---------|---------|
| gateway | 8080 | ✅ YES | API Gateway (frontend) |
| auth-service | 8081 | ❌ NO | Internal only |
| user-service | 8082 | ❌ NO | Internal only |
| chat-service | 8083 | ❌ NO | Internal only |
| postgres | 5432 | ❌ NO | Internal only |
| redis | 6379 | ❌ NO | Internal only |
| kafka | 9092 | ❌ NO | Internal only |
| realtime-edge | 8089 | ✅ YES | WebSocket (if exposed) |

**Verification**: Only gateway and realtime-edge should be exposed to host

---

## Nginx Configuration

### Frontend Reverse Proxy

**Expected nginx.conf** (if present):
```nginx
upstream api_gateway {
    server gateway:8080;
}

server {
    listen 80;
    server_name example.com;
    
    location /api {
        proxy_pass http://api_gateway;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Correlation-Id $request_id;
    }
    
    location /ws {
        proxy_pass http://api_gateway;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

**Verification**: Check if nginx is configured correctly

---

## SSL/TLS Configuration

### Production

**Expected**:
```yaml
certbot:
  image: certbot/certbot
  volumes:
    - ./letsencrypt:/etc/letsencrypt
  command: certonly --webroot -w /var/www/html -d example.com
```

**Current Status** (Local): Likely not needed

**Production**: Must have SSL certificates

---

## Volume Configuration

### Data Persistence

**Expected**:
```yaml
volumes:
  postgres_data:
    driver: local
  redis_data:
    driver: local
  kafka_data:
    driver: local
```

**Verification**: Services have persistent volumes

---

## Logging Configuration

### Docker Compose Logging

**Current**: Likely logs to stdout (default)

**Can be verified**:
```bash
docker-compose logs gateway
docker-compose logs -f chat-service
docker-compose logs --tail=50 auth-service
```

---

## Memory & CPU Limits

### Performance Tuning

**Should add resource limits**:
```yaml
gateway:
  deploy:
    resources:
      limits:
        cpus: '1'
        memory: 512M
      reservations:
        cpus: '0.5'
        memory: 256M
```

**Current**: Likely no limits (all services use default)

---

## Testing Docker Compose

### Verification Commands

```bash
# Check syntax
docker-compose config

# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs

# Test services
curl -X GET http://localhost:8080/actuator/health

# Cleanup
docker-compose down
```

---

## Secrets Management

### Current: Environment Variables

**Risk**: Secrets in docker-compose.yml or .env files

**Better Approach** (production):
- Docker Secrets (Swarm mode)
- HashiCorp Vault
- AWS Secrets Manager

**Local Development**: .env file is acceptable (add to .gitignore)

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| Cloudinary config missing | BLOCKER | upload-service | Add env vars |
| Postgres DB init script | MEDIUM | docker-compose | Add init SQL |
| Healthchecks incomplete | MEDIUM | docker-compose | Add to all services |
| Resource limits not set | LOW | docker-compose | Add memory/CPU limits |
| Nginx SSL not configured | LOW | Production-only | Add Certbot for prod |
| Service ordering | MEDIUM | docker-compose | Verify depends_on correct |
| Secrets hardcoded | MEDIUM | Local-acceptable | Use .env for local |

---

**Next**: Read 20-common-touch-minimization-report.md
