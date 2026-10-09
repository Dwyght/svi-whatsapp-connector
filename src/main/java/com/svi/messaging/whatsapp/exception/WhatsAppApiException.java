package com.svi.messaging.whatsapp.exception;

import com.svi.messaging.common.exception.ExternalFailureCategory;

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
