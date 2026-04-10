## Why

The frontend currently hardcodes individual microservice URLs (ports 8081-8088) in its API configuration. With the backend API gateway now deployed, services are no longer directly exposed; all traffic must route through the gateway on port 8080. This change is essential for local development and production deployment to work correctly.

## What Changes

- Update all REST API clients to use the gateway (http://localhost:8080) instead of individual service ports
- Update WebSocket endpoints to connect through the gateway instead of direct service connections
- Modify API configuration files to centralize the gateway base URL
- Ensure all service-specific paths remain intact (routing is path-based at the gateway layer)
- Update environment-based configuration to support different gateway URLs for dev/prod

## Capabilities

### New Capabilities
- `fe-rest-api-gateway-integration`: Update all REST API clients (auth, user, chat, presence, friend, notification, upload) to route through the gateway while maintaining path-based service routing
- `fe-websocket-gateway-integration`: Update real-time WebSocket connections (chat, presence, friendship) to connect through the gateway instead of direct service endpoints

### Modified Capabilities

- None (this is not modifying existing feature behavior, only how the frontend connects to the backend)

## Impact

- **Frontend files**: `src/config/api.config.ts`, `src/api/clients.ts`, `src/api/base.api.ts`, WebSocket setup files (likely in `src/websocket/`)
- **No breaking changes to user-facing features**: This is purely infrastructure/connectivity
- **Deployment**: Production environment must specify the gateway URL instead of individual service URLs
- **Development**: All developers must use http://localhost:8080 as the base URL for backend communication
