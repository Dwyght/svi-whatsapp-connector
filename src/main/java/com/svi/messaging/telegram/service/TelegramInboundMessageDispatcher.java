package com.svi.messaging.telegram.service;

import com.svi.messaging.telegram.model.TelegramDispatchResult;
import com.svi.messaging.telegram.model.TelegramInboundMessage;

/**
 * Returns ACCEPTED only after taking responsibility for processing the update.
 * Production implementations must persist updates and deduplicate by updateId.
 */
public interface TelegramInboundMessageDispatcher {
	TelegramDispatchResult dispatch(TelegramInboundMessage message);
}
