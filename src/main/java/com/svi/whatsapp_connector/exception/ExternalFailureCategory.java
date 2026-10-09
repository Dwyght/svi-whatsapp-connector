package com.svi.whatsapp_connector.exception;

public enum ExternalFailureCategory {
	INVALID_REQUEST,
	AUTHENTICATION,
	THROTTLED,
	TIMEOUT,
	TRANSIENT,
	MALFORMED_RESPONSE,
	PERMANENT
}
