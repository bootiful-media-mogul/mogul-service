package com.joshlong.mogul.api;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * a settings change has to reach every node, because every node is holding its own cache
 * of the thing that just changed.
 */
@Configuration
class RabbitMqConfiguration {

	static String settingsEventsExchangeName(String destination) {
		return destination + "-fanout";
	}

	private final String exchangeName;

	RabbitMqConfiguration(ApiProperties properties) {
		this.exchangeName = settingsEventsExchangeName(properties.amqp().settingsEvents());
	}

	@Bean
	InitializingBean mogulSettingsEventsAmqpInitializer(AmqpAdmin amqpAdmin,
			FanoutExchange mogulSettingsEventsExchange) {
		return () -> amqpAdmin.declareExchange(mogulSettingsEventsExchange);
	}

	@Bean
	FanoutExchange mogulSettingsEventsExchange() {
		return ExchangeBuilder.fanoutExchange(this.exchangeName).durable(true).build();
	}

}
