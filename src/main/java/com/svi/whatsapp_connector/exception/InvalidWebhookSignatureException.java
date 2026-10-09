package com.svi.whatsapp_connector.exception;

public class InvalidWebhookSignatureException extends RuntimeException {
	public InvalidWebhookSignatureException() {
		super("Webhook authentication failed");
	}
}
