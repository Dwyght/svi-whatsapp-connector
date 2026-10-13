package com.svi.messaging.telegram.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.svi.messaging.ai.client.AiClient;
import com.svi.messaging.ai.dto.AiRequest;
import com.svi.messaging.ai.dto.AiResponse;
import com.svi.messaging.ai.exception.AiClientException;
import com.svi.messaging.telegram.client.TelegramApiClient;
import com.svi.messaging.telegram.model.TelegramInboundMessage;
import org.junit.jupiter.api.Test;

class TelegramMessageProcessingServiceTests {
	private final AiClient aiClient = mock(AiClient.class);
	private final TelegramApiClient apiClient = mock(TelegramApiClient.class);
	private final TelegramMessageProcessingService service = new TelegramMessageProcessingService(aiClient, apiClient);
	private final TelegramInboundMessage message = new TelegramInboundMessage(1, 42, 2, 7L, "Synthetic test text");
	private final AiRequest request = new AiRequest("telegram:42:7", "Synthetic test text");

	@Test
	void repliesToTheSameChatAndTopicUsingTheSharedAiBoundary() {
		when(aiClient.generateResponse(request)).thenReturn(new AiResponse("Synthetic test reply"));
		service.process(message);
		verify(apiClient).sendTextMessage(42, 7L, "Synthetic test reply");
	}

	@Test
	void rejectsMissingBlankAndOversizedAiResponsesWithoutSending() {
		for (var response : new AiResponse[] {null, new AiResponse(" "), new AiResponse("x".repeat(4097))}) {
			when(aiClient.generateResponse(request)).thenReturn(response);
			assertThatThrownBy(() -> service.process(message)).isInstanceOf(AiClientException.class);
		}
		verifyNoInteractions(apiClient);
	}
}
