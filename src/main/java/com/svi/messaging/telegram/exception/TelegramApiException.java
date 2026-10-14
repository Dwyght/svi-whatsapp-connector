package com.svi.messaging.telegram.exception;

import com.svi.messaging.common.exception.ExternalFailureCategory;

public class TelegramApiException extends RuntimeException {
	private final ExternalFailureCategory category;

	public TelegramApiException(ExternalFailureCategory category, String message) {
		this(category, message, null);
	}

	public TelegramApiException(ExternalFailureCategory category, String message, Throwable cause) {
		super(message, cause);
		this.category = category;
	}

	public ExternalFailureCategory category() {
		return category;
	}
}
