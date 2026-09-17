package com.joshlong.mogul.api;

import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.scalars.ExtendedScalars;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.TimeZone;

@IntegrationComponentScan
@ImportRuntimeHints(ApiApplication.EventHints.class)
@EnableConfigurationProperties(ApiProperties.class)
@SpringBootApplication
public class ApiApplication {

	static void main(String[] args) {
		// pinned, not inherited. the PostgreSQL JDBC driver issues a `SET TimeZone` to
		// the JVM's default on every connection, so this decides how the database reads
		// and writes every timestamp. it happens to be UTC in production today, by
		// virtue of the container; saying so here means a base image or a region change
		// can't quietly move it. must run before the context starts, so the first
		// connection already has it.
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
		var app = new SpringApplication(ApiApplication.class);
		app.setApplicationStartup(new BufferingApplicationStartup(1024 * 4));
		app.run(args);
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
		return http -> http //
			.authorizeHttpRequests((authorize) -> authorize //
				.requestMatchers(EndpointRequest.toAnyEndpoint())
				.permitAll() //
				.requestMatchers("/public/**")
				.permitAll()//
			);
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

	static class EventHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
			for (var c : new Class<?>[] { ApplicationEvent.class, EventObject.class })
				hints.reflection().registerType(c, MemberCategory.values());

		}

	}

}