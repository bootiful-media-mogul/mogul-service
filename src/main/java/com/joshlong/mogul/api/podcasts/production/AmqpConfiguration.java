package com.joshlong.mogul.api.podcasts.production;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static com.joshlong.mogul.api.utils.RabbitUtils.defineDestination;

@Configuration
class AmqpConfiguration {

	@Bean
	InitializingBean podcastProcessingInitialization(AmqpAdmin amqp, ApiProperties properties) {
		return () -> Set
			.of(properties.podcasts().production().amqp().replies(),
					properties.podcasts().production().amqp().requests())
			.forEach(q -> defineDestination(amqp, q, q, q));
	}

}
