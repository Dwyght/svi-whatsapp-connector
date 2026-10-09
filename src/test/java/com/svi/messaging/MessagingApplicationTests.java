package com.svi.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.svi.messaging.ai.client.AiClient;
import com.svi.messaging.ai.client.MockAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MessagingApplicationTests {
	@Autowired
	private AiClient aiClient;

	@Test
	void contextLoads() {
		assertThat(aiClient).isInstanceOf(MockAiClient.class);
	}

}
