package com.svi.messaging.ai.client;

import com.svi.messaging.ai.config.AiProperties;
import com.svi.messaging.ai.dto.AiRequest;
import com.svi.messaging.ai.dto.AiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "mock")
public class MockAiClient implements AiClient {
	private static final String PREFIX = "Mock AI response: ";

	private final int maximumResponseCharacters;

	public MockAiClient(AiProperties properties) {
		this.maximumResponseCharacters = properties.maxResponseCharacters();
	}

	@Override
	public AiResponse generateResponse(AiRequest request) {
		String response = PREFIX + request.message();
		if (response.length() > maximumResponseCharacters) {
			response = response.substring(0, maximumResponseCharacters);
		}
		return new AiResponse(response);
	}
}
