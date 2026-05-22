package com.chatweb.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.chatweb.user.service.impl.UserProfileService;

@SpringBootTest(classes = UserServiceApplication.class)
class ChatappApplicationTests {

	@MockBean
	private UserProfileService userProfileService;

	@Test
	void contextLoads() {
	}

}
