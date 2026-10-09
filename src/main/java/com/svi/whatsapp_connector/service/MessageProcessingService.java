package com.svi.whatsapp_connector.service;

import com.svi.whatsapp_connector.client.AiClient;
import com.svi.whatsapp_connector.client.WhatsAppApiClient;
import com.svi.whatsapp_connector.exception.AiClientException;
import com.svi.whatsapp_connector.exception.ExternalFailureCategory;
import com.svi.whatsapp_connector.model.AiRequest;
import com.svi.whatsapp_connector.model.AiResponse;
import com.svi.whatsapp_connector.model.InboundMessage;
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
