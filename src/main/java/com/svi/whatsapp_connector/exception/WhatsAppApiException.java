package com.svi.whatsapp_connector.exception;

public class WhatsAppApiException extends RuntimeException {
	private final ExternalFailureCategory category;

	public WhatsAppApiException(ExternalFailureCategory category, String message) {
		super(message);
		this.category = category;
	}

	public WhatsAppApiException(ExternalFailureCategory category, String message, Throwable cause) {
		super(message, cause);
		this.category = category;
	}

	public ExternalFailureCategory category() {
		return category;
	}
}
