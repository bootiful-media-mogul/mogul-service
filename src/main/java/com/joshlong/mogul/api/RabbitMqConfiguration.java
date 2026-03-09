package com.joshlong.mogul.api;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitMqConfiguration {

	private static final String BEAN_PREFIX = "mogulSettingsEvents";

	private static final String QUEUE_NAME = BEAN_PREFIX + "Queue";

	private static final String EXCHANGE_NAME = BEAN_PREFIX + "Exchange";

	private static final String BINDING_NAME = BEAN_PREFIX + "Binding";

	private final String queueName;

	RabbitMqConfiguration(ApiProperties properties) {
		this.queueName = properties.amqp().settingsEvents();
	}

	@Bean
	InitializingBean mogulSettingsEventsAmqpInitializer(AmqpAdmin amqpAdmin,
			@Qualifier(QUEUE_NAME) Queue mogulEventsQueue, @Qualifier(EXCHANGE_NAME) Exchange mogulEventsExchange,
			@Qualifier(BINDING_NAME) Binding mogulSettingsEventsBinding) {
		return () -> {
			amqpAdmin.declareQueue(mogulEventsQueue);
			amqpAdmin.declareExchange(mogulEventsExchange);
			amqpAdmin.declareBinding(mogulSettingsEventsBinding);
		};
	}

	@Bean(BINDING_NAME)
	Binding mogulSettingsEventsBinding(@Qualifier(QUEUE_NAME) Queue mogulEventsQueue,
			@Qualifier(EXCHANGE_NAME) Exchange mogulEventsExchange) {
		return BindingBuilder //
			.bind(mogulEventsQueue) //
			.to(mogulEventsExchange)//
			.with(this.queueName) //
			.noargs();
	}

	@Bean(QUEUE_NAME)
	Queue mogulSettingsEventsQueue() {
		return QueueBuilder.durable(this.queueName).build();
	}

	@Bean(EXCHANGE_NAME)
	Exchange mogulSettingsEventsExchange() {
		return ExchangeBuilder.directExchange(this.queueName).build();
	}

}
