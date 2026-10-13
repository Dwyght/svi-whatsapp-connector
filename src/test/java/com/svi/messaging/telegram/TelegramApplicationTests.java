package com.svi.messaging.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.svi.messaging.telegram.controller.TelegramWebhookController;
import com.svi.messaging.telegram.security.TelegramWebhookFilter;
import com.svi.messaging.telegram.service.TelegramInboundMessageDispatcher;
import com.svi.messaging.whatsapp.controller.WhatsAppWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"telegram.enabled=true", "telegram.bot-token=123:test-token",
		"telegram.webhook-secret-token=test-webhook-secret"})
@ActiveProfiles("test")
class TelegramApplicationTests {
	@Autowired
	private ApplicationContext context;

	@Test
	void wiresTelegramAlongsideWhatsAppWithoutDuplicateControllers() {
		assertThat(context.getBeansOfType(TelegramWebhookController.class)).hasSize(1);
		assertThat(context.getBeansOfType(TelegramWebhookFilter.class)).hasSize(1);
		assertThat(context.getBeansOfType(TelegramInboundMessageDispatcher.class)).hasSize(1);
		assertThat(context.getBeansOfType(WhatsAppWebhookController.class)).hasSize(1);
	}
}
