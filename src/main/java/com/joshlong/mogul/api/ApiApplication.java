package com.joshlong.mogul.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.format.DateTimeFormatter;

@IntegrationComponentScan
@EnableConfigurationProperties(ApiProperties.class)
@SpringBootApplication
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

	@Bean
	SecurityFilterChain myActuatorAwareOauth2ResourceServerConfiguration(HttpSecurity http) throws Exception {
		return http.oauth2ResourceServer((rs) -> rs.jwt(Customizer.withDefaults()))
			.authorizeHttpRequests((authorize) -> authorize.requestMatchers(EndpointRequest.toAnyEndpoint())
				.permitAll()
				.anyRequest()
				.authenticated())
			.build();
	}

	@Bean
	DateTimeFormatter dateTimeFormatter() {
		return DateTimeFormatter.BASIC_ISO_DATE;
	}

}
