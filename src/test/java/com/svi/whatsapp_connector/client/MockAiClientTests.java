package com.svi.whatsapp_connector.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.svi.whatsapp_connector.model.AiRequest;
import com.svi.whatsapp_connector.support.TestProperties;
import org.junit.jupiter.api.Test;

class MockAiClientTests {
	@Test
	void returnsDeterministicResponseWithoutCredentials() {
		MockAiClient client = new MockAiClient(TestProperties.mockAi());

		assertThat(client.generateResponse(new AiRequest("conversation", "Hello")).text())
				.isEqualTo("Mock AI response: Hello");
	}
}
