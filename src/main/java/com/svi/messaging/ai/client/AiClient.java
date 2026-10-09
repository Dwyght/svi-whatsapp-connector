package com.svi.messaging.ai.client;

import com.svi.messaging.ai.dto.AiRequest;
import com.svi.messaging.ai.dto.AiResponse;

public interface AiClient {
	AiResponse generateResponse(AiRequest request);
}
