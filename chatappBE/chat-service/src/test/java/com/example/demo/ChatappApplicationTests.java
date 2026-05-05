package com.example.chat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		classes = ChatServiceApplication.class,
		properties = {
				"services.user.url=http://localhost",
				"services.friendship.url=http://localhost"
		}
)
class ChatappApplicationTests {

	@Test
	void contextLoads() {
	}

}
