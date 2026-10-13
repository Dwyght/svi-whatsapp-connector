package com.svi.messaging.telegram.config;

import java.net.http.HttpClient;

import com.svi.messaging.ai.client.AiClient;
import com.svi.messaging.telegram.client.TelegramApiClient;
import com.svi.messaging.telegram.controller.TelegramWebhookController;
import com.svi.messaging.telegram.exception.TelegramExceptionHandler;
import com.svi.messaging.telegram.mapper.TelegramMessageMapper;
import com.svi.messaging.telegram.security.TelegramWebhookFilter;
import com.svi.messaging.telegram.security.TelegramWebhookTokenVerifier;
import com.svi.messaging.telegram.service.InMemoryTelegramInboundMessageDispatcher;
import com.svi.messaging.telegram.service.TelegramInboundMessageDispatcher;
import com.svi.messaging.telegram.service.TelegramMessageProcessingService;
import com.svi.messaging.telegram.service.TelegramWebhookService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TelegramProperties.class)
@Import({TelegramWebhookController.class, TelegramExceptionHandler.class})
public class TelegramConnectorConfig {
	@Bean
	@Qualifier("telegramRestClient")
	RestClient telegramRestClient(RestClient.Builder builder, TelegramProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.readTimeout());
		// Telegram embeds credentials in the URL; do not export it in HTTP traces.
		return builder.clone().baseUrl("https://api.telegram.org").requestFactory(factory)
				.observationRegistry(ObservationRegistry.NOOP).build();
	}

	@Bean
	TelegramApiClient telegramApiClient(@Qualifier("telegramRestClient") RestClient restClient,
			TelegramProperties properties) {
		return new TelegramApiClient(restClient, properties);
	}

	@Bean
	TelegramMessageMapper telegramMessageMapper() {
		return new TelegramMessageMapper();
	}

	@Bean
	TelegramMessageProcessingService telegramMessageProcessingService(
			AiClient aiClient, TelegramApiClient apiClient) {
		return new TelegramMessageProcessingService(aiClient, apiClient);
	}

	@Bean
	@Profile({"local", "test"})
	@ConditionalOnMissingBean(TelegramInboundMessageDispatcher.class)
	InMemoryTelegramInboundMessageDispatcher telegramInboundMessageDispatcher(
			TelegramMessageProcessingService processingService, TelegramProperties properties,
			MeterRegistry registry) {
		return new InMemoryTelegramInboundMessageDispatcher(processingService, properties, registry);
	}

	@Bean
	TelegramWebhookService telegramWebhookService(tools.jackson.databind.ObjectMapper objectMapper,
			TelegramMessageMapper mapper, TelegramInboundMessageDispatcher dispatcher,
			MeterRegistry registry) {
		return new TelegramWebhookService(objectMapper, mapper, dispatcher, registry);
	}

	@Bean
	TelegramWebhookTokenVerifier telegramWebhookTokenVerifier(TelegramProperties properties) {
		return new TelegramWebhookTokenVerifier(properties);
	}

	@Bean
	TelegramWebhookFilter telegramWebhookFilter(TelegramProperties properties,
			TelegramWebhookTokenVerifier verifier) {
		return new TelegramWebhookFilter(properties, verifier);
	}
}
