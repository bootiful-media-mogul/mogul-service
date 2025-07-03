package com.joshlong.mogul.api.notifications;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.ably.integration.AblyHeaders;
import com.joshlong.mogul.api.notifications.ably.integration.AblyMessageHandler;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.event.inbound.ApplicationEventListeningMessageProducer;
import org.springframework.integration.transformer.AbstractTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Integrates Ably for real-time publish/subscribe notifications. This code adapts all
 * messages received via the {@link com.joshlong.mogul.api.notifications.NotificationEvent
 * notification events} subsystem into calls to the Ably online realtime-communication
 * API.
 *
 * @author Josh Long
 */

@Configuration
class AblyIntegrationConfiguration {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Bean
	Channel notificationsChannel(AblyRealtime ablyRealtime, MogulService mogulService) {
		var pfb = new ProxyFactoryBean();
		pfb.setTargetClass(Channel.class);
		pfb.setProxyTargetClass(true);
		pfb.addAdvice((MethodInterceptor) _ -> {
			var channelName = AblyNotificationsUtils.ablyNoticationsChannelFor(mogulService.getCurrentMogul());
			log.debug("resolving notification channel name {}", channelName);
			return ablyRealtime.channels.get(channelName);
		});
		return (Channel) pfb.getObject();
	}

	@Bean
	ApplicationEventListeningMessageProducer applicationEventListeningMessageProducer() {
		var aelm = new ApplicationEventListeningMessageProducer();
		aelm.setEventTypes(NotificationEvent.class);
		return aelm;
	}

	@Bean
	AblyMessageHandler ablyMessageHandler(AblyRealtime ablyRealtime) {
		return new AblyMessageHandler(ablyRealtime);
	}

	// i have a hackney'd version of the code that publishes messages to a named topic
	// i am able to publish a NotificationEvent and then, in an integration flow, see it
	// call the outbound support for Ably
	// so now we'd test it in javascript, make sure its using the short lived token
	// strategy (maybe all of this gets turned
	// into javascript at some point?)
	@Bean
	IntegrationFlow notificationEventsToAblyOutbundIntegrationFlow(ApplicationEventListeningMessageProducer aemp,
			AblyMessageHandler messageHandler) {
		return IntegrationFlow.from(aemp) //
			.transform(new AbstractTransformer() {
				@Override
				protected Object doTransform(Message<?> message) {
					// todo once we have this all working, let's look at how to setup
					// encryption / decryption for the values sent to/from Ably.
					// but how would the in-browser client decrypt a value we encrypted
					// here on the server?
					if (message.getPayload() instanceof NotificationEvent notificationEvent) {
						var topic = AblyNotificationsUtils.ablyNoticationsChannelFor(notificationEvent.mogulId());
						return MessageBuilder //
							.withPayload(notificationEvent.mogulId() + ':' + notificationEvent.key()) //
							.setHeader(AblyHeaders.ABLY_NAME, topic)//
							.setHeader(AblyHeaders.ABLY_CHANNEL_NAME, topic)//
							.build();
					}
					return null;
				}
			})//
			.handle(messageHandler)//
			.get();
	}

}
