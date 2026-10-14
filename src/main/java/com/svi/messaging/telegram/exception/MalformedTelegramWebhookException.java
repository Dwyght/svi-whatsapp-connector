package com.svi.messaging.telegram.exception;

public class MalformedTelegramWebhookException extends RuntimeException {
	public MalformedTelegramWebhookException() {
		super("Malformed Telegram webhook payload");
	}
}
