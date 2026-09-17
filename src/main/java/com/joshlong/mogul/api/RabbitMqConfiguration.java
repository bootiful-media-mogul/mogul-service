package com.joshlong.mogul.api;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * a settings change has to reach <em>every</em> node, because every node is holding its
 * own cache of the thing that just changed. the topology this replaces -- a direct
 * exchange and one durable queue shared by all the consumers -- made those consumers
 * competing consumers, so each message went to exactly one of them and the rest carried
 * on serving what they already had until it expired on its own. that is invisible with a
 * single node of each deployable and wrong the moment there are two. a fanout exchange,
 * with each consumer declaring a queue of its own, gives every node a copy.
 * <p>
 * only the exchange is declared here. the queues belong to the consumers -- a consumer
 * that shares a queue with another consumer is the bug this exists to fix -- so each one
 * declares its own and binds it.
 *
 * @author Josh Long
 */
@Configuration
class RabbitMqConfiguration {

	/**
	 * the exchange is deliberately <em>not</em> named for
	 * {@code mogul.amqp.settings-events} on its own. that name is already taken by the
	 * direct exchange this replaces, and re-declaring an existing exchange under a new
	 * type is a {@code PRECONDITION_FAILED} that fails the app at startup rather than
	 * anywhere convenient. the suffix sidesteps it, and the old exchange and queue can be
	 * deleted whenever it suits. every consumer has to derive the name the same way.
	 */
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
