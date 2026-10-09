package com.svi.whatsapp_connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WhatsappConnectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(WhatsappConnectorApplication.class, args);
	}

}
