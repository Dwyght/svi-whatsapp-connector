package com.svi.messaging.telegram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import com.svi.messaging.telegram.exception.MalformedTelegramWebhookException;
import com.svi.messaging.telegram.mapper.TelegramMessageMapper;
import com.svi.messaging.telegram.model.TelegramDispatchResult;
import com.svi.messaging.telegram.model.TelegramInboundMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TelegramWebhookServiceTests {
	private final TelegramInboundMessageDispatcher dispatcher = mock(TelegramInboundMessageDispatcher.class);
	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final TelegramWebhookService service = new TelegramWebhookService(
			JsonMapper.builder().build(), new TelegramMessageMapper(), dispatcher, registry);

	@Test
	void parsesUnknownProviderFieldsAndPropagatesDispatcherResults() {
		var message = new TelegramInboundMessage(1, 42, 2, null, "Synthetic test text");
		for (var result : TelegramDispatchResult.values()) {
			when(dispatcher.dispatch(message)).thenReturn(result);
			assertThat(service.handle(bytes("""
					{"update_id":1,"message":{"message_id":2,"date":123,"from":{"id":9,"is_bot":false},
					 "chat":{"id":42,"type":"private"},"text":"Synthetic test text"}}
					"""))).isEqualTo(result);
		}
	}

	@Test
	void acknowledgesUnsupportedUpdatesWithoutDispatching() {
		assertThat(service.handle(bytes("{\"update_id\":1,\"callback_query\":{}}")))
				.isEqualTo(TelegramDispatchResult.ACCEPTED);
		verifyNoInteractions(dispatcher);
		assertThat(registry.get("telegram.webhook.events").tag("result", "ignored").counter().count()).isEqualTo(1);
	}

	@Test
	void rejectsMalformedJsonMissingIdentifiersAndJsonNull() {
		for (var json : new String[] {"{", "null", "{}", "{\"update_id\":\"invalid\"}", "[]"}) {
			assertThatThrownBy(() -> service.handle(bytes(json))).isInstanceOf(MalformedTelegramWebhookException.class);
		}
		verifyNoInteractions(dispatcher);
	}

	private byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}
}
