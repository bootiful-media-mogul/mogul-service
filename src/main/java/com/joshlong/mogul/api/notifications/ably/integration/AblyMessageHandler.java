package com.joshlong.mogul.api.notifications.ably.integration;

import io.ably.lib.realtime.Channel;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.MessageExtras;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;

import static com.joshlong.mogul.api.notifications.ably.integration.AblyHeaders.*;

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
		var headers = message.getHeaders();
		var name = headers.get(ABLY_NAME, String.class);
		var clientId = headers.get(ABLY_CLIENT_ID, String.class);
		var extras = headers.get(ABLY_MESSAGE_EXTRAS, MessageExtras.class);
		var ablyMessage = new io.ably.lib.types.Message(name, message.getPayload(), clientId, extras);
		try {
			this.channel.publish(new io.ably.lib.types.Message[] { ablyMessage });
			if (this.log.isDebugEnabled())
				this.log.debug("published {}:{}", name, message.getPayload());
		} //
		catch (AblyException e) {
			this.log.error("oops!", e);
			throw new RuntimeException(e);
		}
	}

}
