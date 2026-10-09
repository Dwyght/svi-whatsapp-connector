package com.svi.whatsapp_connector.client;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import com.svi.whatsapp_connector.config.WhatsAppProperties;
import com.svi.whatsapp_connector.dto.request.SendTextMessageRequest;
import com.svi.whatsapp_connector.dto.response.SendMessageResponse;
import com.svi.whatsapp_connector.exception.ExternalFailureCategory;
import com.svi.whatsapp_connector.exception.WhatsAppApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WhatsAppApiClient {
	private final RestClient restClient;
	private final WhatsAppProperties properties;

	public WhatsAppApiClient(@Qualifier("whatsAppRestClient") RestClient restClient,
			WhatsAppProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public String sendTextMessage(String recipientWaId, String text) {
		if (recipientWaId == null || !recipientWaId.matches("\\d{5,20}")
				|| !StringUtils.hasText(text) || text.length() > 4096) {
			throw new WhatsAppApiException(
					ExternalFailureCategory.INVALID_REQUEST,
					"Invalid outbound WhatsApp text message");
		}
		SendTextMessageRequest request = SendTextMessageRequest.to(recipientWaId, text);
		for (int attempt = 1; attempt <= properties.outboundMaxAttempts(); attempt++) {
			try {
				SendMessageResponse response = restClient.post()
						.uri("/{version}/{phoneNumberId}/messages",
								properties.graphApiVersion(), properties.phoneNumberId())
						.body(request)
						.retrieve()
						.body(SendMessageResponse.class);
				String messageId = response == null ? null : response.firstMessageId();
				if (!StringUtils.hasText(messageId)) {
					throw new WhatsAppApiException(
							ExternalFailureCategory.MALFORMED_RESPONSE,
							"WhatsApp API returned no message identifier");
				}
				return messageId;
			}
			catch (RestClientResponseException exception) {
				if (exception.getStatusCode().value() == 429
						&& attempt < properties.outboundMaxAttempts()) {
					waitBeforeRetry(exception);
					continue;
				}
				throw mapResponseFailure(exception);
			}
			catch (ResourceAccessException exception) {
				throw mapNetworkFailure(exception);
			}
		}
		throw new WhatsAppApiException(
				ExternalFailureCategory.PERMANENT, "WhatsApp API retry policy exhausted");
	}

	private WhatsAppApiException mapResponseFailure(RestClientResponseException exception) {
		HttpStatusCode status = exception.getStatusCode();
		ExternalFailureCategory category;
		if (status.value() == 401 || status.value() == 403) {
			category = ExternalFailureCategory.AUTHENTICATION;
		}
		else if (status.value() == 429) {
			category = ExternalFailureCategory.THROTTLED;
		}
		else if (status.is4xxClientError()) {
			category = ExternalFailureCategory.INVALID_REQUEST;
		}
		else if (status.is5xxServerError()) {
			category = ExternalFailureCategory.TRANSIENT;
		}
		else {
			category = ExternalFailureCategory.PERMANENT;
		}
		return new WhatsAppApiException(category,
				"WhatsApp API request failed with HTTP " + status.value(), exception);
	}

	private WhatsAppApiException mapNetworkFailure(ResourceAccessException exception) {
		Throwable cause = exception.getMostSpecificCause();
		ExternalFailureCategory category = cause instanceof HttpTimeoutException
				|| cause instanceof java.net.SocketTimeoutException
				? ExternalFailureCategory.TIMEOUT
				: ExternalFailureCategory.TRANSIENT;
		String message = cause instanceof ConnectException
				? "Could not connect to WhatsApp API"
				: "WhatsApp API network request failed";
		return new WhatsAppApiException(category, message, exception);
	}

	private void waitBeforeRetry(RestClientResponseException exception) {
		Duration delay = retryAfter(exception.getResponseHeaders() == null
				? null
				: exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER));
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new WhatsAppApiException(
					ExternalFailureCategory.TRANSIENT,
					"WhatsApp API retry interrupted", interrupted);
		}
	}

	private Duration retryAfter(String header) {
		Duration maximum = properties.outboundMaxRetryDelay();
		if (!StringUtils.hasText(header)) {
			return maximum;
		}
		try {
			Duration requested = Duration.ofSeconds(Math.max(0, Long.parseLong(header)));
			return requested.compareTo(maximum) > 0 ? maximum : requested;
		}
		catch (NumberFormatException exception) {
			return maximum;
		}
	}
}
