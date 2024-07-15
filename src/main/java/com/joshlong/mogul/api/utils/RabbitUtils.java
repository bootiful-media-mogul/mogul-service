package com.joshlong.mogul.api.utils;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.QueueBuilder;

public abstract class RabbitUtils {

	public static void defineDestination(AmqpAdmin amqpAdmin, String exchange, String queue, String routingKey) {

		var q = QueueBuilder.durable(queue).build();
		amqpAdmin.declareQueue(q);

		var e = ExchangeBuilder.topicExchange(exchange).durable(true).build();
		amqpAdmin.declareExchange(e);

		var b = BindingBuilder.bind(q).to(e).with(routingKey).noargs();
		amqpAdmin.declareBinding(b);
	}

}
