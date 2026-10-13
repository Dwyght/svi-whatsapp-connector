package com.svi.messaging.telegram.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import com.svi.messaging.telegram.config.TelegramProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TelegramWebhookFilterTests {
	private final TelegramProperties properties = new TelegramProperties("123:test-token", "test-webhook-secret",
			Duration.ofSeconds(3), Duration.ofSeconds(10), 1024, 1, 1, 10,
			Duration.ofHours(1), Duration.ofSeconds(1));
	private final TelegramWebhookFilter filter = new TelegramWebhookFilter(
			properties, new TelegramWebhookTokenVerifier(properties));

	@Test
	void boundsRequestsWithoutContentLength() throws Exception {
		var request = new MockHttpServletRequest("POST", "/webhooks/telegram") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		request.addHeader(TelegramWebhookTokenVerifier.HEADER_NAME, "test-webhook-secret");
		request.setContent(new byte[1025]);
		var response = new MockHttpServletResponse();
		var called = new AtomicBoolean();
		filter.doFilter(request, response, (req, res) -> called.set(true));
		assertThat(response.getStatus()).isEqualTo(413);
		assertThat(called).isFalse();
	}

	@Test
	void leavesOtherConnectorRequestsUntouched() throws Exception {
		var request = new MockHttpServletRequest("POST", "/webhooks/whatsapp");
		var response = new MockHttpServletResponse();
		var called = new AtomicBoolean();
		filter.doFilter(request, response, (req, res) -> called.set(true));
		assertThat(called).isTrue();
	}
}
