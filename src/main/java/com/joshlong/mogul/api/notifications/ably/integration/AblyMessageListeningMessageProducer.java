package com.joshlong.mogul.api.notifications.ably.integration;

import io.ably.lib.realtime.Channel;
import org.springframework.integration.endpoint.MessageProducerSupport;

import java.util.Objects;

/**
 * the genesis of a Spring Integration
 * {@link org.springframework.integration.dsl.IntegrationFlow flow}
 *
 * @author Josh Long
 */
public class AblyMessageListeningMessageProducer extends MessageProducerSupport {

	private final AblyMessageListenerContainer ablyMessageListenerContainer;

	public AblyMessageListeningMessageProducer(Channel channel) {
		super();
		var messageListener = (MessageListener) message -> {
			var msg = getMessageBuilderFactory()//
				.withPayload(message.getPayload()) //
				.copyHeadersIfAbsent(message.getHeaders()) //
				.build();
			sendMessage(msg);
		};
		this.ablyMessageListenerContainer = new AblyMessageListenerContainer(channel, messageListener);
	}

	@Override
	public String getComponentType() {
		return "event:ably-channel-adapter";
	}

	@Override
	protected void onInit() {
		super.onInit();
		var imaginedBeanName = getBeanName() + "AblyMessageListeningMessageProducer";
		logger.debug("setting bean name to " + imaginedBeanName + " for AblyMessageListenerContainer ");
		this.ablyMessageListenerContainer.setBeanName(Objects.requireNonNull(imaginedBeanName));
		this.logger.info("onInit");
	}

	@Override
	protected void doStart() {
		super.doStart();
		this.ablyMessageListenerContainer.start();
	}

	@Override
	protected void doStop() {
		super.doStop();
		this.ablyMessageListenerContainer.stop();
	}

}
