package com.svi.whatsapp_connector.client;

import com.svi.whatsapp_connector.model.AiRequest;
import com.svi.whatsapp_connector.model.AiResponse;

public interface AiClient {
	AiResponse generateResponse(AiRequest request);
}
