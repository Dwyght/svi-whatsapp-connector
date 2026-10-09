package com.svi.messaging.whatsapp.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.svi.messaging.whatsapp.config.OutboundMessagingProperties;
import com.svi.messaging.whatsapp.exception.OutboundAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Profile({"local", "test"})
public class LocalOutboundAuthorizationInterceptor implements HandlerInterceptor {
	private static final String BEARER_PREFIX = "Bearer ";

	private final byte[] expectedAuthorization;

	public LocalOutboundAuthorizationInterceptor(OutboundMessagingProperties properties) {
		this.expectedAuthorization = (BEARER_PREFIX + properties.localAuthToken())
				.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		byte[] supplied = authorization == null
				? new byte[0]
				: authorization.getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expectedAuthorization, supplied)) {
			throw new OutboundAuthenticationException();
		}
		return true;
	}
}
