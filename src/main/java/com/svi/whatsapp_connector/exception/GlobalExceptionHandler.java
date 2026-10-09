package com.svi.whatsapp_connector.exception;

import java.time.Instant;

import com.svi.whatsapp_connector.dto.response.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(InvalidWebhookSignatureException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidSignature() {
		return error(HttpStatus.UNAUTHORIZED, "Webhook authentication failed");
	}

	@ExceptionHandler(MalformedWebhookException.class)
	ResponseEntity<ApiErrorResponse> handleMalformedWebhook() {
		return error(HttpStatus.BAD_REQUEST, "Malformed webhook payload");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
		log.error("Unhandled request failure; type={}", exception.getClass().getSimpleName());
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(Instant.now(), status.value(), message));
	}
}
