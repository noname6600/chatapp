## Purpose
Define the requirement for the gateway to operate correctly behind an Nginx reverse proxy in production, preserving route and policy behavior for forwarded requests, and ensuring CORS response headers are not duplicated at the ingress path.

## Requirements

### Requirement: Gateway is proxy-compatible behind Nginx
Gateway deployment SHALL operate correctly when all external API traffic enters through Nginx and is forwarded to gateway on internal network using api.chatweb.nani.id.vn as ingress host.

#### Scenario: API request forwarded by Nginx
- **WHEN** Nginx forwards a valid API request received on api.chatweb.nani.id.vn to gateway
- **THEN** gateway routes the request to the correct downstream service and returns the downstream response

#### Scenario: Forwarded host/path preservation
- **WHEN** an API request includes the production API host and route path
- **THEN** gateway receives sufficient forwarded request context to apply route and policy behavior consistently

### Requirement: CORS response remains non-duplicated at ingress path
Production ingress SHALL avoid duplicate conflicting CORS response headers between Nginx and gateway/app responses.

#### Scenario: Gateway returns CORS headers
- **WHEN** gateway produces CORS response headers for an API response
- **THEN** the final response observed by browser clients contains no duplicate conflicting `Access-Control-Allow-Origin` value

#### Scenario: Browser preflight through proxy
- **WHEN** a browser sends CORS preflight request via api.chatweb.nani.id.vn
- **THEN** the response includes a coherent CORS policy that passes browser validation
