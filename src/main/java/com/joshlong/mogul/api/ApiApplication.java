package com.joshlong.mogul.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.springframework.security.config.Customizer.withDefaults;

@IntegrationComponentScan
@EnableConfigurationProperties(ApiProperties.class)
@SpringBootApplication
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

	@Bean
	SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((authorize) -> authorize //
			.requestMatchers(EndpointRequest.toAnyEndpoint())
			.permitAll() //
			.anyRequest()
			.authenticated()//
		);
		http.oauth2ResourceServer((resourceServer) -> resourceServer.jwt(withDefaults()));
		return http.build();
	}

	@Bean
	DateTimeFormatter dateTimeFormatter() {
		return DateTimeFormatter.BASIC_ISO_DATE;
	}

}

