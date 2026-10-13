package com.svi.messaging.telegram.service;

import com.svi.messaging.ai.client.AiClient;
import com.svi.messaging.ai.dto.AiRequest;
import com.svi.messaging.ai.exception.AiClientException;
import com.svi.messaging.common.exception.ExternalFailureCategory;
import com.svi.messaging.telegram.client.TelegramApiClient;
import com.svi.messaging.telegram.model.TelegramInboundMessage;
import org.springframework.util.StringUtils;

public class TelegramMessageProcessingService {
	private final AiClient aiClient;
	private final TelegramApiClient telegramApiClient;

	public TelegramMessageProcessingService(AiClient aiClient, TelegramApiClient telegramApiClient) {
		this.aiClient = aiClient;
		this.telegramApiClient = telegramApiClient;
	}

	public void process(TelegramInboundMessage message) {
		var response = aiClient.generateResponse(new AiRequest(message.conversationId(), message.text()));
		if (response == null || !StringUtils.hasText(response.text()) || response.text().length() > 4096) {
			throw new AiClientException(ExternalFailureCategory.MALFORMED_RESPONSE,
					"AI provider returned an invalid Telegram text response", null);
		}
		telegramApiClient.sendTextMessage(message.chatId(), message.messageThreadId(), response.text());
	}
}
