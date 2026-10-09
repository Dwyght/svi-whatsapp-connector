package com.svi.whatsapp_connector.dto.response;

import java.time.Instant;

public record ApiErrorResponse(Instant timestamp, int status, String error) {
}
