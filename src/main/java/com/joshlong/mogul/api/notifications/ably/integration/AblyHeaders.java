package com.joshlong.mogul.api.notifications.ably.integration;

/**
 * the headers for messages arriving in from Ably.
 *
 * @author Josh Long
 */
public interface AblyHeaders {

	String ABLY_NAME = "ably_name";

	String ABLY_CHANNEL_NAME = "ably_channel_name";

	String ABLY_MESSAGE_EXTRAS = "ably_messageExtras";

	String ABLY_ID = "ably_id";

	String ABLY_CLIENT_ID = "ably_clientId";

	String ABLY_CONNECTION_ID = "ably_connectionId";

	String ABLY_TIMESTAMP = "ably_timestamp";

	String ABLY_CONNECTION_KEY = "ably_connectionKey";

}
