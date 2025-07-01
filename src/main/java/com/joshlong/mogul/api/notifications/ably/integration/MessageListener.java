package com.joshlong.mogul.api.notifications.ably.integration;

/**
 * @author Josh Long
 */
public interface MessageListener {

	void onMessage(org.springframework.messaging.Message<?> message) throws Exception;

}
