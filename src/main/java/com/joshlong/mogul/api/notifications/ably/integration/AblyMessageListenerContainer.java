package com.joshlong.mogul.api.notifications.ably.integration;

import io.ably.lib.realtime.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class AblyMessageListenerContainer implements SmartLifecycle, BeanNameAware, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(AblyMessageListenerContainer.class);

	private final AtomicBoolean running = new AtomicBoolean(false);

	private final AtomicReference<String> beanName = new AtomicReference<>();

	private final Channel channel;

	private final MessageListener messageListener;

	AblyMessageListenerContainer(Channel channel, MessageListener messageListener) {
		this.messageListener = messageListener;
		this.channel = channel;
	}

	@Override
	public void start() {
		try {
			if (this.running.compareAndSet(false, true))
				this.listenForMessages();
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void listenForMessages() throws Exception {
		try {
			this.channel.subscribe(message -> {
				var msg = MessageBuilder //
					.withPayload(message.data) //
					.setHeader(AblyHeaders.ABLY_NAME, message.name) //
					.setHeader(AblyHeaders.ABLY_ID, message.id) //
					.setHeader(AblyHeaders.ABLY_CLIENT_ID, message.clientId) //
					.setHeader(AblyHeaders.ABLY_TIMESTAMP, message.timestamp) //
					.setHeader(AblyHeaders.ABLY_MESSAGE_EXTRAS, message.extras) //
					.setHeader(AblyHeaders.ABLY_CONNECTION_ID, message.connectionId) //
					.setHeader(AblyHeaders.ABLY_CONNECTION_KEY, message.connectionKey) //
					.build();

				try {
					messageListener.onMessage(msg);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}

			});
		}
		catch (Throwable throwable) {
			log.warn("caught an exception while listening for messages on {}", this.beanName.get(), throwable);
			throw new RuntimeException(throwable);
		}

	}

	@Override
	public void stop() {
		if (this.running.compareAndSet(true, false)) {
			log.debug("stopped listening for messages on {}", this.beanName.get());
		}
	}

	@Override
	public boolean isRunning() {
		return this.running.get();
	}

	@Override
	public void setBeanName(String name) {
		this.beanName.set(name);
	}

	@Override
	public void destroy() throws Exception {
		this.stop();
	}

}
