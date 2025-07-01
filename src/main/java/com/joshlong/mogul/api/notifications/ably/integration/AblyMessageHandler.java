package com.joshlong.mogul.api.notifications.ably.integration;

import io.ably.lib.realtime.Channel;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.MessageExtras;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;

import java.util.function.Supplier;

/**
 * Support for sending Ably messages as outbound messages.
 *
 * @author Josh Long
 */
public class AblyMessageHandler implements MessageHandler {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Channel channel;

	public AblyMessageHandler(Channel channel) {
		this.channel = channel;
		Assert.notNull(this.channel, "the Ably realtime channel must not be null");
	}

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		var name = valueOrNull(message.getHeaders(), String.class, AblyHeaders.ABLY_NAME, () -> null);
		var ablyMessage = new io.ably.lib.types.Message(name, message.getPayload(),
				valueOrNull(message.getHeaders(), String.class, AblyHeaders.ABLY_CLIENT_ID, () -> null),
				valueOrNull(message.getHeaders(), MessageExtras.class, AblyHeaders.ABLY_MESSAGE_EXTRAS, () -> null));

		try {
			this.channel.publish(new io.ably.lib.types.Message[] { ablyMessage });
			log.info("published {}:{}", name, message.getPayload());
		} //
		catch (AblyException e) {
			this.log.error("oops!", e);
			throw new RuntimeException(e);
		}
	}

	private @Nullable <T> T valueOrNull(MessageHeaders messageHeaders, Class<T> clzz, String headerName,
			Supplier<T> defaultSupplier) {
		if (messageHeaders.containsKey(headerName))
			return messageHeaders.get(headerName, clzz);
		return null == defaultSupplier ? null : defaultSupplier.get();
	}

}
