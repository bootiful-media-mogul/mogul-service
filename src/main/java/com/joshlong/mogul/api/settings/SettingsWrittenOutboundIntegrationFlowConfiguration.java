package com.joshlong.mogul.api.settings;

import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.event.inbound.ApplicationEventListeningMessageProducer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Configuration
class SettingsWrittenOutboundIntegrationFlowConfiguration {

	static final String AUTHENTICATION_AND_SETTINGS_EVENT_LISTENER_BEAN_NAME = "authenticationAndSettingsEventApplicationEventListeningMessageProducer";

	@Bean(AUTHENTICATION_AND_SETTINGS_EVENT_LISTENER_BEAN_NAME)
	ApplicationEventListeningMessageProducer authenticationAndSettingsEventApplicationEventListeningMessageProducer() {
		var messageProducer = new ApplicationEventListeningMessageProducer();
		messageProducer.setEventTypes(AuthenticationAndSettingsEvent.class);
		return messageProducer;
	}

	/**
	 * published to the fanout exchange declared alongside it, so that every node holding
	 * a cache of these settings hears about the change and not just whichever one a
	 * shared queue happened to hand the message to. a fanout ignores the routing key, so
	 * there isn't one.
	 */
	@Bean
	IntegrationFlow settingsWrittenEventExternalizationIntegrationFlow(AmqpTemplate amqpTemplate, ObjectMapper json,
			FanoutExchange mogulSettingsEventsExchange,
			@Qualifier(AUTHENTICATION_AND_SETTINGS_EVENT_LISTENER_BEAN_NAME) ApplicationEventListeningMessageProducer authenticationAndSettingsEventApplicationEventListeningMessageProducer) {
		var log = LoggerFactory.getLogger(this.getClass());
		var exchange = mogulSettingsEventsExchange.getName();
		log.info("exchange is {}.", exchange);
		return IntegrationFlow //
			.from(authenticationAndSettingsEventApplicationEventListeningMessageProducer) //
			.transform((GenericTransformer<AuthenticationAndSettingsEvent, Map<String, String>>) source -> Map.of(
					"authenticationName", source.authentication().getName(), "category", source.category(), "key",
					source.key())) //
			.transform((GenericTransformer<Map<String, String>, String>) jsonMap -> {
				var jsonResult = json.writeValueAsString(jsonMap);
				log.info("sending {}", jsonResult);
				return jsonResult;
			}) //
			.handle(Amqp.outboundAdapter(amqpTemplate).exchangeName(exchange)) //
			.get();
	}

	record AuthenticationAndSettingsEvent(String category, String key, Authentication authentication) {

	}

	@Component
	@Transactional
	static class AuthenticatedSettingsEventListener {

		private final ApplicationEventPublisher publisher;

		AuthenticatedSettingsEventListener(ApplicationEventPublisher publisher) {
			this.publisher = publisher;
		}

		@EventListener
		void onSettingsWrittenEvent(SettingWrittenEvent event) {
			this.publisher.publishEvent(new AuthenticationAndSettingsEvent(event.category(), event.key(),
					SecurityContextHolder.getContext().getAuthentication()));
		}

	}

}
