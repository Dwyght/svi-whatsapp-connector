package com.svi.messaging.whatsapp.exception;

public class InvalidWebhookSignatureException extends RuntimeException {
	public InvalidWebhookSignatureException() {
		super("Webhook authentication failed");
	}
}
