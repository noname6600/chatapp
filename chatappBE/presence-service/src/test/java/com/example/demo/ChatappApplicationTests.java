package com.example.presence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		classes = PresenceServiceApplication.class,
		properties = {
				"presence.redis.listener.enabled=false"
		}
)
class ChatappApplicationTests {

	@Test
	void contextLoads() {
	}

}
