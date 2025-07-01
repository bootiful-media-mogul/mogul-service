package com.joshlong.mogul.api.notifications.ably;

import com.joshlong.mogul.api.ApiProperties;
import com.joshlong.mogul.api.notifications.ably.integration.AblyHeaders;
import com.joshlong.mogul.api.notifications.ably.integration.AblyMessageListeningMessageProducer;
import com.joshlong.mogul.api.notifications.ably.integration.AblyOutboundMessageHandler;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integrates Ably for real-time publish/subscribe notifications. This code adapts all
 * messages received via the {@link com.joshlong.mogul.api.notifications.NotificationEvent
 * notification events} subsystem into calls to the Ably online realtime-communication
 * API.
 *
 * @author Josh Long
 */

@Configuration
@ImportRuntimeHints(AblyHints.class)
class AblyConfiguration {

	private static final Logger log = LoggerFactory.getLogger(AblyConfiguration.class);

	private static final String ABLY_NOTIFICATIONS_CHANNEL_NAME = "notifications";

	@Bean
	AblyRealtime ablyRealtime(ApiProperties mogulProperties) throws Exception {
		return new AblyRealtime(mogulProperties.notifications().ably().apiKey());
	}

	@Bean
	Channel channel(AblyRealtime ablyRealtime) {
		return ablyRealtime.channels.get(ABLY_NOTIFICATIONS_CHANNEL_NAME);
	}

	@Bean
	IntegrationFlow ablyOutboundFlow(Channel channel) {
		var ctr = new AtomicInteger(0);
		return IntegrationFlow //
			.from(() -> {
				if (ctr.incrementAndGet() == 1) {
					log.info("sending a message to Ably");
					return MessageBuilder //
						.withPayload((Object) ("hello, Spring Integration @ " + Instant.now())) //
						.setHeader(AblyHeaders.ABLY_NAME, "test")//
						.build();
				}
				return null;
			}) //
			.handle(new AblyOutboundMessageHandler(channel))//
			.get();
	}

	@Bean
	AblyMessageListeningMessageProducer ablyMessageListeningMessageProducer(Channel channel) {
		return new AblyMessageListeningMessageProducer(channel);
	}

	@Bean
	IntegrationFlow ablyInboundFlow(AblyMessageListeningMessageProducer mps) {
		return IntegrationFlow //
			.from(mps) //
			.handle((payload, headers) -> { //
				log.info("received a file [{}] from Ably, with headers {}", payload, headers.entrySet());
				return null;
			})//
			.get();
	}

}
