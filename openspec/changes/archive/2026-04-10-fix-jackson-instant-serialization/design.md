## Context

Spring Boot 3.x ships with `jackson-datatype-jsr310` on the classpath via `spring-boot-starter-web`. Its auto-configuration (`JacksonAutoConfiguration`) produces an `ObjectMapper` with `JavaTimeModule` pre-registered, making `java.time.Instant` serialization work out of the box.

Two services bypass this by declaring their own `@Bean ObjectMapper` with a bare `new ObjectMapper()`:

| Service | Class | Bean source |
|---|---|---|
| `notification-service` | `com.example.notification.kafka.KafkaConsumerConfig` | Kafka consumer config |
| `friendship-service` | `com.example.friendship.configuration.JacksonConfig` | Dedicated Jackson config |

Because a custom `@Bean` wins over auto-configuration, `JavaTimeModule` is absent from both beans. Every REST response or Kafka payload that carries a `java.time.Instant` field (`NotificationResponse.createdAt`, `Friendship.createdAt`, `Friendship.updatedAt`) fails at serialization with:

```
HttpMessageConversionException: Type definition error: [simple type, class java.time.Instant]
```

Other services (`auth-service`, `user-service`, `chat-service`) already use `JsonMapper.builder().findAndAddModules().build()` in their own `KafkaConfiguration` beans and work correctly.

## Goals / Non-Goals

**Goals:**
- Both affected `ObjectMapper` beans register `JavaTimeModule` so `Instant` serializes to ISO-8601 string format
- Fix matches the established pattern across the codebase
- No changes to services that are already working

**Non-Goals:**
- Consolidating all `ObjectMapper` beans into a shared common module (out of scope)
- Changing serialization format for other data types
- Adding Jackson customizations beyond `JavaTimeModule` registration

## Decisions

### Decision: Use `JsonMapper.builder().findAndAddModules().build()` over manual `registerModule(new JavaTimeModule())`

**Chosen**: `JsonMapper.builder().findAndAddModules().build()`

**Rationale**:
- `findAndAddModules()` auto-discovers all Jackson modules present on the classpath (including `JavaTimeModule`), making the bean self-maintaining
- Consistent with the pattern in `auth-service`, `user-service`, and `chat-service`
- If additional modules are added as dependencies later (e.g., for Kotlin data classes), they are automatically included without a code change

**Alternative considered**: `new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)`
- Works but requires explicit enumeration; inconsistent with project pattern; `common-core/JacksonConfig` already uses a manual approach and adding a third variant would increase fragmentation

### Decision: Keep `notification-service` bean in `KafkaConsumerConfig` (not move to a new class)

**Rationale**: Moving the `ObjectMapper` bean to a dedicated `JacksonConfig` class in notification-service would be a structural improvement but is beyond the scope of this bugfix. Fixing in place minimises the diff.

## Risks / Trade-offs

- [Risk] `findAndAddModules()` could pick up an unexpected Jackson module added transitively → Mitigation: the Spring Boot BOM controls module versions; risk is negligible; other services have used this pattern without issue.
- [Risk] `friendship-service` KafkaProducer may have its own independent `ObjectMapper` reference → Mitigation: checked existing code — `FriendshipEventProducer` uses the Spring-managed `KafkaTemplate`, which inherits the application-context `ObjectMapper`. No separate instance exists.

## Migration Plan

1. Update `notification-service/KafkaConsumerConfig.objectMapper()` — one-line change
2. Update `friendship-service/JacksonConfig.objectMapper()` — one-line change
3. Run `.\gradlew.bat :notification-service:compileJava :friendship-service:compileJava` to confirm no regressions
4. Run `.\gradlew.bat :notification-service:test :friendship-service:test` to confirm existing tests pass
5. No database migration, no API contract change, no rolling-deploy requirement — safe to deploy to all instances simultaneously
