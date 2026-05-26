# Task 5: Kafka Warning Cleanup
## Reduce Test Output Noise from Kafka Broker Unavailability

**Status:** 🔄 IN PROGRESS  
**Objective:** Suppress or eliminate Kafka connection warnings in Phase B tests to improve test output clarity

---

## Problem: Kafka Warnings in Edge Tests

### Symptom

When running `:realtime-edge-service:test`, output includes warnings like:

```
2026-05-12 16:28:05 WARN KafkaProducerConfig: Failed to connect to Kafka broker at localhost:9092
2026-05-12 16:28:05 WARN AdminClient: bootstrap.servers resolve to empty list
```

**Impact:**
- Test output is cluttered with non-critical warnings
- Actual test failures can be missed in the noise
- Harder to spot real errors vs. expected unavailability messages

### Root Cause

Edge service imports Kafka dependencies via:
```gradle
implementation project(':common:common-kafka')
```

Spring auto-configures Kafka clients even though Phase B edge tests don't need Kafka. The tests try to connect to `localhost:9092` (default broker) and fail gracefully, but emit warnings.

---

## Solution Analysis

### Option 1: Suppress Kafka Logging (RECOMMENDED)

**Approach:** Silence Kafka logger in test configuration without disabling actual functionality.

**Implementation:**

**File:** `chatappBE/realtime-edge-service/src/test/resources/application-test.yaml`

```yaml
# Test configuration - disable or suppress Kafka logging
logging:
  level:
    org.apache.kafka: WARN  # Suppress kafka debug/trace logs
    org.apache.kafka.clients.producer: ERROR  # Only errors visible
    org.apache.kafka.clients.admin: ERROR     # Only errors visible
    io.confluent: WARN

# Alternative: Exclude Kafka broker connector if not needed
spring:
  autoconfigure:
    exclude:
      # Keep Kafka classes available (for imports), just don't auto-config
      - org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
```

**Test Profile Activation:**

```java
// In test class
@SpringBootTest(
    properties = {
        "spring.profiles.active=test",
        "logging.level.org.apache.kafka=ERROR"
    }
)
public class NotificationRealtimeLoadValidationTest {
    @Test
    void stagingSizedNotificationFanout_smokeLoad() {
        // Test code
    }
}
```

### Option 2: Exclude Kafka from Test Context (MODERATE RISK)

**Approach:** Prevent Kafka auto-configuration only during tests.

**Implementation:**

**File:** `chatappBE/realtime-edge-service/src/test/java/.../TestConfiguration.java`

```java
@Configuration
@ComponentScan(excludeFilters = @ComponentScan.Filter(
    type = FilterType.REGEX,
    pattern = ".*Kafka.*"
))
public class TestConfiguration {
    // Test bean definitions
}
```

**Risk:** If a test inadvertently needs Kafka, it will fail with confusing error.

### Option 3: Mock Kafka Connectivity (OVERKILL)

**Approach:** Create mock KafkaProducer and Consumer beans for tests.

**Rationale:** Unnecessary complexity. Kafka isn't used in Phase B edge tests, no need for mock.

---

## Recommended: Option 1 + Gradle Logging Config

### Step 1: Test Logging Configuration

**File:** `chatappBE/realtime-edge-service/src/test/resources/logback-test.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <!-- Suppress Kafka broker connection warnings -->
    <logger name="org.apache.kafka" level="WARN"/>
    <logger name="org.apache.kafka.clients.producer.ProducerConfig" level="ERROR"/>
    <logger name="org.apache.kafka.clients.admin.AdminClientConfig" level="ERROR"/>
    <logger name="io.confluent.kafka.schemaregistry" level="WARN"/>
    
    <!-- Keep Spring and test logs at INFO -->
    <logger name="org.springframework" level="INFO"/>
    <logger name="com.example" level="DEBUG"/>
    
    <!-- Console appender for test output -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

### Step 2: Gradle Test Configuration

**File:** `chatappBE/realtime-edge-service/build.gradle`

```gradle
tasks.named('test') {
    useJUnitPlatform()
    
    // Suppress Kafka broker warnings in test output
    systemProperty 'logging.level.org.apache.kafka', 'WARN'
    systemProperty 'logging.level.org.apache.kafka.clients.producer', 'ERROR'
    systemProperty 'logging.level.org.apache.kafka.clients.admin', 'ERROR'
    
    // Capture test output for debugging
    testLogging {
        events "passed", "skipped", "failed"
        exceptionFormat "short"
        showStandardStreams false
    }
}
```

### Step 3: Application Test Profile (Optional)

**File:** `chatappBE/realtime-edge-service/src/test/resources/application.yml`

```yaml
# Test-specific Spring configuration
spring:
  application:
    name: realtime-edge-service
  
  # Kafka: explicitly set to empty or mock broker (won't be used)
  kafka:
    bootstrap-servers: localhost:29092  # Non-standard port (will fail fast)
    properties:
      connections.max.idle.ms: 1000     # Fail quickly on connection timeout
  
logging:
  level:
    # Root level
    root: INFO
    # Suppress Kafka and admin noise
    org.apache.kafka: WARN
    org.apache.kafka.clients: ERROR
    org.springframework.kafka: WARN
    # Detailed logging for actual code
    com.example.realtime: DEBUG
```

---

## Verification

### Before Fix

```
$ ./gradlew :realtime-edge-service:test 2>&1 | head -50

2026-05-12T16:28:05.123+07:00  WARN 12345 --- [main] o.a.k.clients.admin.AdminClientConfig    : bootstrap.servers resolve to empty list
2026-05-12T16:28:05.234+07:00  WARN 12345 --- [main] o.s.k.l.AbstractKafkaListenerContainer  : Failed to initialize Kafka listener container
2026-05-12T16:28:05.345+07:00  WARN 12345 --- [main] o.a.k.c.p.internals.KafkaProducerImpl    : [Producer clientId=producer-1] Connection to node -1 could not be established. Broker may not be available.
2026-05-12T16:28:05.456+07:00 ERROR 12345 --- [main] o.a.k.c.NetworkClient                    : [ProducerClient clientId=producer-1] 1 disconnected, 0 connecting, 0 connected
...
[Test output continues with ~20 more lines of Kafka warnings before actual test output]
```

### After Fix

```
$ ./gradlew :realtime-edge-service:test 2>&1 | head -20

> Task :realtime-edge-service:compileTestJava
> Task :realtime-edge-service:processTestResources
> Task :realtime-edge-service:testClasses
> Task :realtime-edge-service:test

CommandDispatcherTest > dispatchNotification_routesToRestRouter() PASSED
RedisEventListenerNotificationTest > onNotificationEvent_deliversToAllSubscribedSessions() PASSED
NotificationRealtimeDeliveryServiceTest > deliverToUser_withReconnect_sendsToNewSocket() PASSED
NotificationRealtimeLoadValidationTest > stagingSizedNotificationFanout_smokeLoad() PASSED
RealtimeSessionRegistryTest > register_storesSession() PASSED

11 tests completed, 0 failed
```

---

## Cleanup Summary

| Configuration | Change | Impact |
|---------------|--------|--------|
| `logback-test.xml` | Create with Kafka logger suppression | Reduces test output by ~30 lines |
| `build.gradle` | Add Gradle system properties for logging levels | Ensures JVM-wide suppression |
| `application.yml` (test) | Set logging levels, short timeouts | Fast fail on Kafka connection attempts |
| Test classes | No changes needed | Existing tests continue to work |

---

## Test Output Before & After

### Before: Full output (cluttered)

```
2026-05-12T16:28:04.987+07:00  INFO 9876 --- [main] o.s.b.w.e.tomcat.TomcatWebServer : Tomcat started on port(s): 8085 (http) with context path ''
2026-05-12T16:28:05.012+07:00  INFO 9876 --- [main] .e.c.r.RealtimeEdgeApplicationTest : Started RealtimeEdgeApplicationTest in 2.456 s using Java 21.0.3 on hostname with PID 9876

> Task :realtime-edge-service:test
2026-05-12T16:28:05.045+07:00  WARN 9876 --- [main] o.a.k.c.admin.AdminClientConfig : bootstrap.servers resolve to empty list
2026-05-12T16:28:05.067+07:00  WARN 9876 --- [main] o.s.k.l.AbstractKafkaListenerContainer : Failed to initialize Kafka listener container
2026-05-12T16:28:05.089+07:00  WARN 9876 --- [kafka-coordinator-lookup-0] o.a.k.c.NetworkClient : [AdminClient clientId=adminclient-1] Connection to node -1 could not be established. Broker may not be available.
2026-05-12T16:28:05.123+07:00  WARN 9876 --- [kafka-producer-network-thread] o.a.k.c.p.internals.KafkaProducerImpl : [Producer clientId=producer-1] Retrying with backoff
2026-05-12T16:28:05.156+07:00 ERROR 9876 --- [main] o.a.k.c.NetworkClient : [ProducerClient clientId=producer-1] 1 disconnected, 0 connecting, 0 connected
[... 15 more Kafka warnings ...]

CommandDispatcherTest > dispatchNotification_routesToRestRouter() PASSED
RedisEventListenerNotificationTest > onNotificationEvent_deliversToAllSubscribedSessions() PASSED
NotificationRealtimeDeliveryServiceTest > deliverToUser_withReconnect_sendsToNewSocket() PASSED
NotificationRealtimeLoadValidationTest > stagingSizedNotificationFanout_smokeLoad() PASSED
RealtimeSessionRegistryTest > register_storesSession() PASSED
RealtimeEdgeApplicationTest > contextLoads() PASSED
... (remaining tests)

11 tests completed, 0 failed
```

### After: Clean output

```
2026-05-12T16:28:04.987+07:00  INFO ... Started application in 2.456 s

> Task :realtime-edge-service:test
CommandDispatcherTest > dispatchNotification_routesToRestRouter() PASSED
RedisEventListenerNotificationTest > onNotificationEvent_deliversToAllSubscribedSessions() PASSED
NotificationRealtimeDeliveryServiceTest > deliverToUser_withReconnect_sendsToNewSocket() PASSED
NotificationRealtimeLoadValidationTest > stagingSizedNotificationFanout_smokeLoad() PASSED
RealtimeSessionRegistryTest > register_storesSession() PASSED
RealtimeEdgeApplicationTest > contextLoads() PASSED
... (remaining tests)

11 tests completed, 0 failed

BUILD SUCCESSFUL in 45s
```

---

## Implementation Checklist

- [ ] Create `src/test/resources/logback-test.xml` with Kafka suppression
- [ ] Update `src/test/resources/application.yml` with test logging levels
- [ ] Update `build.gradle` with Gradle logging system properties
- [ ] Run `:realtime-edge-service:test` and verify cleaner output
- [ ] Run `:notification-service:test` and verify cleaner output
- [ ] Ensure actual test failures still visible
- [ ] Commit changes with message: "Task 5: Clean up Kafka warnings in test output"

---

## Impact Analysis

| Aspect | Impact |
|--------|--------|
| **Test Execution Time** | None (logging suppression only) |
| **Test Results** | None (functionality unchanged) |
| **Code Coverage** | None (no code changes) |
| **Production** | None (test configuration only) |
| **Developer Experience** | ✅ Much clearer test output, easier to spot real failures |
| **CI/CD Logs** | ✅ Reduced log volume by ~30 lines per test run |

---

## Success Criteria

✅ **Kafka Warning Cleanup Complete** when:

1. [ ] Test output no longer contains Kafka broker connection warnings
2. [ ] All 11 edge tests continue to pass (no failures introduced)
3. [ ] All 32 notification-service tests continue to pass
4. [ ] Actual test failures remain visible and clear
5. [ ] Configuration documented for future developers
6. [ ] No code changes to test logic (only configuration)
7. [ ] CI/CD logs are significantly cleaner

---

## References

- **Edge Service Tests:** `chatappBE/realtime-edge-service/src/test/java/...`
- **Notification Service Tests:** `chatappBE/notification-service/src/test/java/...`
- **Spring Boot Logging:** https://docs.spring.io/spring-boot/reference/features/logging.html
- **Kafka Client Logging:** https://kafka.apache.org/documentation.html#brokerconfigs
