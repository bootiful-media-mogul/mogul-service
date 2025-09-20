package com.joshlong.mogul.api;

import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.scalars.ExtendedScalars;
import org.flywaydb.core.internal.publishing.PublishingConfigurationExtension;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

import static org.springframework.security.config.Customizer.withDefaults;

@IntegrationComponentScan
@ImportRuntimeHints({ ApiApplication.FlywayHints.class })
@EnableConfigurationProperties(ApiProperties.class)
@SpringBootApplication
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

	@Bean
	CaffeineCacheManager caffeineCacheManager(ApiProperties properties) {
		var ccm = new CaffeineCacheManager();//
		var caffeine = Caffeine.newBuilder()//
			.maximumSize(properties.cache().maxEntries())//
			.expireAfterWrite(Duration.ofDays(1))
			.recordStats();//
		ccm.setCaffeine(caffeine);
		return ccm;
	}

	@Bean
	SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
		return http //
			.authorizeHttpRequests((authorize) -> authorize //
				.requestMatchers(EndpointRequest.toAnyEndpoint())
				.permitAll() //
				.requestMatchers("/public/**")
				.permitAll()
				.anyRequest()
				.authenticated()//
			)//
			.oauth2ResourceServer(resourceServer -> resourceServer.jwt(withDefaults()))//
			.build();
	}

	@Bean
	RuntimeWiringConfigurer runtimeWiringConfigurer() {
		return wiringBuilder -> wiringBuilder.scalar(ExtendedScalars.Json)
			.scalar(ExtendedScalars.DateTime)
			.scalar(ExtendedScalars.Url)
			.scalar(ExtendedScalars.Date);
	}

	@Bean
	DateTimeFormatter dateTimeFormatter() {
		return DateTimeFormatter.BASIC_ISO_DATE;
	}

	// fixes https://github.com/bootiful-media-mogul/mogul-service/issues/69
	// todo can we remove this one day?
	static class FlywayHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			hints.reflection().registerType(PublishingConfigurationExtension.class, MemberCategory.values());
		}

	}

}