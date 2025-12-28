package com.joshlong.mogul.api;

import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.scalars.ExtendedScalars;
import org.flywaydb.core.internal.publishing.PublishingConfigurationExtension;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

@IntegrationComponentScan
@ImportRuntimeHints({ ApiApplication.FlywayHints.class })
@EnableConfigurationProperties(ApiProperties.class)
@SpringBootApplication
public class ApiApplication {

	static void main(String[] args) {
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
	Customizer<HttpSecurity> httpSecurityCustomizer() {
		// @formatter:off
		return http -> http
			.authorizeHttpRequests((authorize) -> authorize //
			.requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll() //
			.requestMatchers("/public/**").permitAll()//
		);
		// @formatter:on
	}

	@Bean
	RuntimeWiringConfigurer runtimeWiringConfigurer() {
		return wiringBuilder -> wiringBuilder //
			.scalar(ExtendedScalars.Json)//
			.scalar(ExtendedScalars.DateTime)//
			.scalar(ExtendedScalars.Url)//
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