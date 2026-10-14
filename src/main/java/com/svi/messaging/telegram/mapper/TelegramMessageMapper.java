package com.svi.messaging.telegram.mapper;

import java.util.Optional;

import com.svi.messaging.telegram.dto.request.TelegramWebhookRequest;
import com.svi.messaging.telegram.exception.MalformedTelegramWebhookException;
import com.svi.messaging.telegram.model.TelegramInboundMessage;
import org.springframework.util.StringUtils;

public class TelegramMessageMapper {
	public Optional<TelegramInboundMessage> map(TelegramWebhookRequest update) {
		if (update == null || update.updateId() == null || update.updateId() < 0) {
			throw new MalformedTelegramWebhookException();
		}
		var message = update.message();
		if (message == null) {
			return Optional.empty();
		}
		if (message.chat() == null || message.chat().id() == null || message.chat().id() == 0
				|| message.chat().type() == null || message.messageId() == null
				|| message.messageId() <= 0) {
			throw new MalformedTelegramWebhookException();
		}
		if (!"private".equals(message.chat().type()) || message.from() == null
				|| !Boolean.FALSE.equals(message.from().bot()) || !StringUtils.hasText(message.text())) {
			return Optional.empty();
		}
		if (message.text().length() > 4096
				|| message.messageThreadId() != null && message.messageThreadId() <= 0) {
			throw new MalformedTelegramWebhookException();
		}
		return Optional.of(new TelegramInboundMessage(update.updateId(), message.chat().id(),
				message.messageId(), message.messageThreadId(), message.text()));
	}
}
