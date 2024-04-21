package com.joshlong.mogul.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.web.servlet.function.RouterFunctions.route;
import static org.springframework.web.servlet.function.ServerResponse.ok;

@IntegrationComponentScan
@EnableConfigurationProperties(ApiProperties.class)
@SpringBootApplication
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> httpRouterResponse() throws Exception {
		return route().GET("/hello", request -> ok().body(Map.of("message", "Hello, world!"))).build();
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

	/*
	 * // @Bean SecurityFilterChain
	 * myActuatorAwareOauth2ResourceServerConfiguration(HttpSecurity http) throws
	 * Exception { return http// .authorizeHttpRequests((authorize) -> authorize //
	 * .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll() //
	 * .anyRequest().authenticated()// )// .oauth2ResourceServer((rs) -> rs
	 * .jwt(Customizer.withDefaults()) )// .build(); }
	 */

	@Bean
	DateTimeFormatter dateTimeFormatter() {
		return DateTimeFormatter.BASIC_ISO_DATE;
	}

}
