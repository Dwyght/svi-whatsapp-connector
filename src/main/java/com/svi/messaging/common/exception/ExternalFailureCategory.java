package com.svi.messaging.common.exception;

public enum ExternalFailureCategory {
	INVALID_REQUEST,
	AUTHENTICATION,
	THROTTLED,
	TIMEOUT,
	TRANSIENT,
	MALFORMED_RESPONSE,
	PERMANENT
}
