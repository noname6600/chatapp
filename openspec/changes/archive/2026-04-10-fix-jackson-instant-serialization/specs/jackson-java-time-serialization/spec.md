## ADDED Requirements

### Requirement: Service ObjectMapper beans must support java.time.Instant serialization
All Spring `@Bean ObjectMapper` instances registered in the application context SHALL include `JavaTimeModule` (or equivalent time-type support) so that `java.time.Instant` values can be serialized to and deserialized from JSON without throwing `HttpMessageConversionException`.

#### Scenario: Notification list endpoint returns createdAt as ISO-8601 string
- **WHEN** a client calls `GET /api/v1/notifications` on notification-service
- **THEN** each notification object in the response SHALL include a `createdAt` field formatted as an ISO-8601 timestamp string (e.g., `"2025-01-01T10:00:00Z"`)

#### Scenario: Friendship list endpoint returns createdAt as ISO-8601 string
- **WHEN** a client calls `GET /api/v1/friends` on friendship-service
- **THEN** each friendship object in the response SHALL include `createdAt` and `updatedAt` fields formatted as ISO-8601 timestamp strings

#### Scenario: No runtime serialization exception for Instant fields
- **WHEN** any REST endpoint in notification-service or friendship-service returns a response DTO containing a `java.time.Instant` field
- **THEN** the HTTP response SHALL complete with a 2xx status and a valid JSON body, with no `HttpMessageConversionException` thrown
