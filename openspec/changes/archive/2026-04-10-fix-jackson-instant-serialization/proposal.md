## Why

`notification-service` and `friendship-service` each define a custom `ObjectMapper` Spring bean using `new ObjectMapper()` without registering `JavaTimeModule`, overriding Spring Boot's auto-configured ObjectMapper. Any REST response or Kafka payload that contains a `java.time.Instant` field (e.g., `NotificationResponse.createdAt`, `Friendship.createdAt/updatedAt`) throws `HttpMessageConversionException: Type definition error: [simple type, class java.time.Instant]` at runtime.

## What Changes

- **notification-service** `KafkaConsumerConfig.objectMapper()`: Replace `new ObjectMapper()` with `JsonMapper.builder().findAndAddModules().build()` so `JavaTimeModule` is auto-discovered.
- **friendship-service** `JacksonConfig.objectMapper()`: Replace `new ObjectMapper()` with the same `JsonMapper.builder().findAndAddModules().build()` pattern, aligning with the pattern already used in `auth-service`, `user-service`, and `chat-service`.
- Both fixes produce an `ObjectMapper` that serializes `Instant` as an ISO-8601 string (consistent with the rest of the backend).

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- None

## Impact

- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/KafkaConsumerConfig.java` — `objectMapper` bean updated
- `chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/JacksonConfig.java` — `objectMapper` bean updated
- No API contract changes; `Instant` fields that were previously broken will serialize to ISO-8601 strings
- No new dependencies required (`jackson-databind` and `jackson-datatype-jsr310` already transitively present via `spring-boot-starter-web`)
