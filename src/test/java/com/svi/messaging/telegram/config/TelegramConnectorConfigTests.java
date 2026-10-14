package com.svi.messaging.telegram.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.svi.messaging.ai.client.AiClient;
import com.svi.messaging.telegram.client.TelegramApiClient;
import com.svi.messaging.telegram.controller.TelegramWebhookController;
import com.svi.messaging.telegram.security.TelegramWebhookFilter;
import com.svi.messaging.telegram.service.InMemoryTelegramInboundMessageDispatcher;
import com.svi.messaging.telegram.service.TelegramInboundMessageDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TelegramConnectorConfigTests {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TelegramScanConfig.class)
			.withBean(RestClient.Builder.class, RestClient::builder)
			.withBean(AiClient.class, () -> mock(AiClient.class))
			.withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
			.withBean(MeterRegistry.class, SimpleMeterRegistry::new);
	private final String[] enabledProperties = {"telegram.enabled=true",
			"telegram.bot-token=123:test-token", "telegram.webhook-secret-token=test-webhook-secret"};

	@Test
	void remainsDisabledWithoutAnyTelegramSettingsOrSecrets() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(TelegramProperties.class);
			assertThat(context).doesNotHaveBean(TelegramApiClient.class);
			assertThat(context).doesNotHaveBean(TelegramWebhookController.class);
			assertThat(context).doesNotHaveBean(TelegramWebhookFilter.class);
		});
	}

	@Test
	void wiresLocalConnectorWithValidatedDefaultsAndOneController() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
				.withPropertyValues(enabledProperties).run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(TelegramWebhookController.class);
					assertThat(context).hasSingleBean(TelegramWebhookFilter.class);
					assertThat(context).hasSingleBean(InMemoryTelegramInboundMessageDispatcher.class);
					assertThat(context.getBean(TelegramProperties.class).webhookMaxRequestBytes()).isEqualTo(262144);
				});
	}

	@Test
	void requiresDurableDispatcherOutsideLocalAndTest() {
		runner.withPropertyValues(enabledProperties).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("TelegramInboundMessageDispatcher");
		});
		runner.withPropertyValues(enabledProperties)
				.withBean(TelegramInboundMessageDispatcher.class, () -> mock(TelegramInboundMessageDispatcher.class))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(InMemoryTelegramInboundMessageDispatcher.class);
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {"telegram.bot-token=invalid", "telegram.webhook-secret-token=bad token",
			"telegram.connect-timeout=0s", "telegram.read-timeout=61s",
			"telegram.deduplication-ttl=8d", "telegram.queue-capacity-per-worker=0"})
	void rejectsInvalidEnabledConfiguration(String invalidProperty) {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
				.withPropertyValues(enabledProperties).withPropertyValues(invalidProperty)
				.run(context -> assertThat(context).hasFailed());
	}

	@TestConfiguration(proxyBeanMethods = false)
	@ComponentScan(basePackageClasses = TelegramConnectorConfig.class)
	@ConfigurationPropertiesScan(basePackageClasses = TelegramProperties.class)
	static class TelegramScanConfig {
	}
}
