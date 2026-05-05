package com.example.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		classes = NotificationServiceApplication.class,
		properties = {
				"notification.redis.listener.enabled=false"
		}
)
class ChatappApplicationTests {

	@Test
	void contextLoads() {
	}

}
