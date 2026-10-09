package com.svi.messaging.whatsapp.exception;

public class InvalidOutboundRequestException extends RuntimeException {
	public InvalidOutboundRequestException(String message) {
		super(message);
	}
}
