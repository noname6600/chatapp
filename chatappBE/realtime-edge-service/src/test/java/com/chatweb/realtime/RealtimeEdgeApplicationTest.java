package com.chatweb.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bootstrap test for realtime-edge service.
 */
@SpringBootTest(properties = "realtime.redis.listener.enabled=false")
@Import(TestConfig.class)
class RealtimeEdgeApplicationTest {

    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
