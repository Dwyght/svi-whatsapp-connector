package com.svi.messaging.whatsapp.exception;

public class DuplicateOutboundSubmissionException extends RuntimeException {
	public DuplicateOutboundSubmissionException(String message) {
		super(message);
	}
}
