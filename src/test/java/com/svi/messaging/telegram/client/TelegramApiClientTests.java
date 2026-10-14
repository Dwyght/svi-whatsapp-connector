package com.svi.messaging.telegram.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;

import com.svi.messaging.common.exception.ExternalFailureCategory;
import com.svi.messaging.telegram.config.TelegramProperties;
import com.svi.messaging.telegram.exception.TelegramApiException;
import com.svi.messaging.telegram.dto.request.TelegramSendTextMessageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramApiClientTests {
	private MockRestServiceServer server;
	private TelegramApiClient client;

	@BeforeEach
	void setUp() {
		var builder = RestClient.builder().baseUrl("https://api.telegram.org");
		server = MockRestServiceServer.bindTo(builder).build();
		var properties = new TelegramProperties("123:test-token", "test-webhook-secret",
				Duration.ofSeconds(3), Duration.ofSeconds(10), 1024, 1, 1, 10,
				Duration.ofHours(1), Duration.ofSeconds(1));
		client = new TelegramApiClient(builder.build(), properties);
		assertThat(properties.toString()).doesNotContain("123:test-token", "test-webhook-secret");
	}

	@AfterEach
	void tearDown() {
		server.verify();
	}

	@Test
	void redactsOutboundPayloadWhenConvertedToDiagnosticText() {
		var request = new TelegramSendTextMessageRequest(42, 7L, "Synthetic private text");
		assertThat(request.toString()).doesNotContain("Synthetic private text", "chatId=42");
	}

	@Test
	void sendsPlainTextToTheCorrectChatAndTopic() {
		server.expect(requestTo("https://api.telegram.org/bot123:test-token/sendMessage"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("""
						{"chat_id":42,"message_thread_id":7,"text":"Synthetic test reply"}
						""", true))
				.andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":3}}", MediaType.APPLICATION_JSON));
		assertThat(client.sendTextMessage(42, 7L, "Synthetic test reply")).isEqualTo(3);
	}

	@Test
	void omitsTheTopicForOrdinaryMessages() {
		server.expect(requestTo("https://api.telegram.org/bot123:test-token/sendMessage"))
				.andExpect(content().json("{\"chat_id\":42,\"text\":\"Synthetic test reply\"}", true))
				.andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":3}}", MediaType.APPLICATION_JSON));
		assertThat(client.sendTextMessage(42, null, "Synthetic test reply")).isEqualTo(3);
	}

	@ParameterizedTest
	@CsvSource({"400,INVALID_REQUEST", "401,AUTHENTICATION", "403,AUTHENTICATION",
			"429,THROTTLED", "500,TRANSIENT", "503,TRANSIENT"})
	void categorizesProviderHttpErrorsWithoutRetrying(int status, ExternalFailureCategory category) {
		server.expect(requestTo("https://api.telegram.org/bot123:test-token/sendMessage"))
				.andRespond(withStatus(HttpStatusCode.valueOf(status)));
		assertFailure(category);
	}

	@Test
	void categorizesAnErrorEnvelopeEvenWithSuccessfulHttpStatus() {
		server.expect(requestTo("https://api.telegram.org/bot123:test-token/sendMessage"))
				.andRespond(withSuccess("{\"ok\":false,\"error_code\":429}", MediaType.APPLICATION_JSON));
		assertFailure(ExternalFailureCategory.THROTTLED);
	}

	@ParameterizedTest
	@ValueSource(strings = {"null", "{}", "{\"ok\":true}", "{\"ok\":false}",
			"{\"ok\":true,\"result\":{\"message_id\":0}}", "not-json"})
	void rejectsMalformedProviderResponses(String json) {
		server.expect(requestTo("https://api.telegram.org/bot123:test-token/sendMessage"))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
		assertFailure(ExternalFailureCategory.MALFORMED_RESPONSE);
	}

	@Test
	void categorizesTimeoutWithoutRetryingAnAmbiguousSend() {
		server.expect(requestTo("https://api.telegram.org/bot123:test-token/sendMessage"))
				.andRespond(withException(new SocketTimeoutException("Synthetic timeout")));
		assertFailure(ExternalFailureCategory.TIMEOUT);
	}

	@Test
	void validatesRequestsBeforeContactingTelegram() {
		assertThatThrownBy(() -> client.sendTextMessage(0, null, "Synthetic test text"))
				.isInstanceOf(TelegramApiException.class);
		assertThatThrownBy(() -> client.sendTextMessage(42, 0L, "Synthetic test text"))
				.isInstanceOf(TelegramApiException.class);
		assertThatThrownBy(() -> client.sendTextMessage(42, null, " "))
				.isInstanceOf(TelegramApiException.class);
		assertThatThrownBy(() -> client.sendTextMessage(42, null, "x".repeat(4097)))
				.isInstanceOf(TelegramApiException.class);
	}

	private void assertFailure(ExternalFailureCategory category) {
		assertThatThrownBy(() -> client.sendTextMessage(42, null, "Synthetic test text"))
				.isInstanceOfSatisfying(TelegramApiException.class, exception -> {
					assertThat(exception.category()).isEqualTo(category);
					assertThat(exception.getMessage()).doesNotContain("test-token", "Synthetic test text");
				});
	}
}
