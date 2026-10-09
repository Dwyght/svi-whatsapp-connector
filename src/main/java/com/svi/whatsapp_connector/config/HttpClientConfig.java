package com.svi.whatsapp_connector.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {
	@Bean
	@Qualifier("whatsAppRestClient")
	RestClient whatsAppRestClient(RestClient.Builder builder, WhatsAppProperties properties) {
		return builder.clone()
				.baseUrl(properties.graphBaseUrl().toString())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
				.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "openai")
	OpenAIClient openAIClient(AiProperties properties) {
		return OpenAIOkHttpClient.builder()
				.apiKey(properties.apiKey())
				.baseUrl(properties.baseUrl().toString())
				.timeout(properties.timeout())
				.maxRetries(properties.maxRetries())
				.build();
	}
}
