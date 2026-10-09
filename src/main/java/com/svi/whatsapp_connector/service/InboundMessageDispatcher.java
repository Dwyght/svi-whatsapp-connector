package com.svi.whatsapp_connector.service;

import com.svi.whatsapp_connector.model.DispatchResult;
import com.svi.whatsapp_connector.model.InboundMessage;

public interface InboundMessageDispatcher {
	DispatchResult dispatch(InboundMessage message);
}
