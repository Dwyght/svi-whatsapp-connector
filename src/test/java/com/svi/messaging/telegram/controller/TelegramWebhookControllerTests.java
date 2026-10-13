package com.svi.messaging.telegram.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;

import com.svi.messaging.telegram.config.TelegramProperties;
import com.svi.messaging.telegram.exception.TelegramExceptionHandler;
import com.svi.messaging.telegram.mapper.TelegramMessageMapper;
import com.svi.messaging.telegram.model.TelegramDispatchResult;
import com.svi.messaging.telegram.security.TelegramWebhookFilter;
import com.svi.messaging.telegram.security.TelegramWebhookTokenVerifier;
import com.svi.messaging.telegram.service.TelegramInboundMessageDispatcher;
import com.svi.messaging.telegram.service.TelegramWebhookService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class TelegramWebhookControllerTests {
	private static final String BODY = """
			{"update_id":1,"message":{"message_id":2,"from":{"is_bot":false},
			 "chat":{"id":42,"type":"private"},"text":"Synthetic test text"}}
			""";
	private MockMvc mockMvc;
	private TelegramInboundMessageDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = mock(TelegramInboundMessageDispatcher.class);
		var properties = new TelegramProperties("123:test-token", "test-webhook-secret",
				Duration.ofSeconds(3), Duration.ofSeconds(10), 1024, 1, 1, 10,
				Duration.ofHours(1), Duration.ofSeconds(1));
		var service = new TelegramWebhookService(JsonMapper.builder().build(),
				new TelegramMessageMapper(), dispatcher, new SimpleMeterRegistry());
		mockMvc = MockMvcBuilders.standaloneSetup(new TelegramWebhookController(service))
				.setControllerAdvice(new TelegramExceptionHandler())
				.addFilters(new TelegramWebhookFilter(properties, new TelegramWebhookTokenVerifier(properties)))
				.build();
	}

	@Test
	void acknowledgesAcceptedAndDuplicateUpdatesAndRejectsOverload() throws Exception {
		when(dispatcher.dispatch(any())).thenReturn(TelegramDispatchResult.ACCEPTED,
				TelegramDispatchResult.DUPLICATE, TelegramDispatchResult.OVERLOADED);
		for (int status : new int[] {200, 200, 503}) {
			mockMvc.perform(post("/webhooks/telegram").contentType(MediaType.APPLICATION_JSON)
					.header(TelegramWebhookTokenVerifier.HEADER_NAME, "test-webhook-secret").content(BODY))
					.andExpect(status().is(status));
		}
	}

	@Test
	void authenticatesBeforeParsingOrDispatching() throws Exception {
		mockMvc.perform(post("/webhooks/telegram").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isUnauthorized());
		for (var token : new String[] {"wrong-secret", "bad token", "x".repeat(257)}) {
			mockMvc.perform(post("/webhooks/telegram").contentType(MediaType.APPLICATION_JSON)
					.header(TelegramWebhookTokenVerifier.HEADER_NAME, token).content(BODY))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/webhooks/telegram").contentType(MediaType.APPLICATION_JSON)
				.header(TelegramWebhookTokenVerifier.HEADER_NAME, "test-webhook-secret", "wrong-secret").content(BODY))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(dispatcher);
	}

	@Test
	void authenticatesDecodedAndMatrixParameterPaths() throws Exception {
		for (var path : new String[] {"/webhooks/telegra%6D", "/webhooks/telegram;ignored=value"}) {
			mockMvc.perform(post(URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(BODY))
					.andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(dispatcher);
	}

	@Test
	void rejectsOversizedAuthenticatedRequestsBeforeDispatching() throws Exception {
		mockMvc.perform(post("/webhooks/telegram").contentType(MediaType.APPLICATION_JSON)
				.header(TelegramWebhookTokenVerifier.HEADER_NAME, "test-webhook-secret").content(new byte[1025]))
				.andExpect(status().isPayloadTooLarge());
		verifyNoInteractions(dispatcher);
	}

	@Test
	void rejectsMalformedAuthenticatedPayloadsAndAcknowledgesUnsupportedUpdates() throws Exception {
		mockMvc.perform(post("/webhooks/telegram").contentType(MediaType.APPLICATION_JSON)
				.header(TelegramWebhookTokenVerifier.HEADER_NAME, "test-webhook-secret").content("{"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/webhooks/telegram").contentType(MediaType.APPLICATION_JSON)
				.header(TelegramWebhookTokenVerifier.HEADER_NAME, "test-webhook-secret").content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/webhooks/telegram").contentType(MediaType.APPLICATION_JSON)
				.header(TelegramWebhookTokenVerifier.HEADER_NAME, "test-webhook-secret")
				.content("{\"update_id\":1,\"callback_query\":{}}"))
				.andExpect(status().isOk());
		verifyNoInteractions(dispatcher);
	}
}
