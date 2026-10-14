package com.svi.messaging.telegram.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.svi.messaging.telegram.dto.request.TelegramWebhookRequest;
import com.svi.messaging.telegram.dto.request.TelegramWebhookRequest.Chat;
import com.svi.messaging.telegram.dto.request.TelegramWebhookRequest.Message;
import com.svi.messaging.telegram.dto.request.TelegramWebhookRequest.User;
import com.svi.messaging.telegram.exception.MalformedTelegramWebhookException;
import org.junit.jupiter.api.Test;

class TelegramMessageMapperTests {
	private final TelegramMessageMapper mapper = new TelegramMessageMapper();

	@Test
	void mapsPrivateTextWithChannelAndTopicScopedConversation() {
		var result = mapper.map(update("private", false, "Synthetic test text", 7L)).orElseThrow();
		assertThat(result.updateId()).isEqualTo(1);
		assertThat(result.chatId()).isEqualTo(42);
		assertThat(result.messageThreadId()).isEqualTo(7);
		assertThat(result.conversationId()).isEqualTo("telegram:42:7");
		assertThat(mapper.map(update("private", false, "Synthetic test text", null))
				.orElseThrow().conversationId()).isEqualTo("telegram:42");
		assertThat(result.toString()).doesNotContain("Synthetic test text", "chatId=42");
	}

	@Test
	void ignoresNonTextGroupBotAndUnsupportedUpdates() {
		assertThat(mapper.map(new TelegramWebhookRequest(1L, null))).isEmpty();
		assertThat(mapper.map(update("private", false, null, null))).isEmpty();
		assertThat(mapper.map(update("private", false, " ", null))).isEmpty();
		assertThat(mapper.map(update("supergroup", false, "Synthetic test text", null))).isEmpty();
		assertThat(mapper.map(update("private", true, "Synthetic test text", null))).isEmpty();
		assertThat(mapper.map(update("private", null, "Synthetic test text", null))).isEmpty();
	}

	@Test
	void rejectsMalformedEnvelopesAndInvalidMessageFields() {
		assertThatThrownBy(() -> mapper.map(null)).isInstanceOf(MalformedTelegramWebhookException.class);
		assertThatThrownBy(() -> mapper.map(new TelegramWebhookRequest(null, null)))
				.isInstanceOf(MalformedTelegramWebhookException.class);
		assertThatThrownBy(() -> mapper.map(new TelegramWebhookRequest(-1L, null)))
				.isInstanceOf(MalformedTelegramWebhookException.class);
		assertThatThrownBy(() -> mapper.map(new TelegramWebhookRequest(1L,
				new Message(2L, null, new User(false), null, "Synthetic test text"))))
				.isInstanceOf(MalformedTelegramWebhookException.class);
		assertThatThrownBy(() -> mapper.map(update("private", false, "x".repeat(4097), null)))
				.isInstanceOf(MalformedTelegramWebhookException.class);
		assertThatThrownBy(() -> mapper.map(update("private", false, "Synthetic test text", 0L)))
				.isInstanceOf(MalformedTelegramWebhookException.class);
	}

	private TelegramWebhookRequest update(String type, Boolean bot, String text, Long threadId) {
		return new TelegramWebhookRequest(1L, new Message(2L, threadId, new User(bot), new Chat(42L, type), text));
	}
}
