package com.svi.messaging.whatsapp.service;

import com.svi.messaging.ai.client.AiClient;
import com.svi.messaging.whatsapp.client.WhatsAppApiClient;
import com.svi.messaging.ai.exception.AiClientException;
import com.svi.messaging.common.exception.ExternalFailureCategory;
import com.svi.messaging.ai.dto.AiRequest;
import com.svi.messaging.ai.dto.AiResponse;
import com.svi.messaging.whatsapp.model.InboundMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MessageProcessingService {
	private final AiClient aiClient;
	private final WhatsAppApiClient whatsAppApiClient;

	public MessageProcessingService(AiClient aiClient, WhatsAppApiClient whatsAppApiClient) {
		this.aiClient = aiClient;
		this.whatsAppApiClient = whatsAppApiClient;
	}

	public void process(InboundMessage message) {
		AiResponse response = aiClient.generateResponse(
				new AiRequest(message.conversationId(), message.text()));
		if (response == null || !StringUtils.hasText(response.text())) {
			throw new AiClientException(
					ExternalFailureCategory.MALFORMED_RESPONSE,
					"AI provider returned an empty response",
					null);
		}
		whatsAppApiClient.sendTextMessage(message.senderWaId(), response.text());
	}
}
