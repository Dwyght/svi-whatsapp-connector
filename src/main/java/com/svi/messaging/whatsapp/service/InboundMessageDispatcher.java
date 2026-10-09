package com.svi.messaging.whatsapp.service;

import com.svi.messaging.whatsapp.model.DispatchResult;
import com.svi.messaging.whatsapp.model.InboundMessage;

public interface InboundMessageDispatcher {
	DispatchResult dispatch(InboundMessage message);
}
