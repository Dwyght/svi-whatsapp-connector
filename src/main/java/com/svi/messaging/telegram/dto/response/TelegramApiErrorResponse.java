package com.svi.messaging.telegram.dto.response;

import java.time.Instant;

public record TelegramApiErrorResponse(Instant timestamp, int status, String error) {
}
