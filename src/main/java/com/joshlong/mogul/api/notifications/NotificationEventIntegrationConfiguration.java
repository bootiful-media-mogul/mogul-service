package com.joshlong.mogul.api.notifications;

import com.joshlong.mogul.api.notifications.ably.integration.AblyHeaders;
import com.joshlong.mogul.api.notifications.ably.integration.AblyMessageHandler;
import com.joshlong.mogul.api.utils.JsonUtils;
import io.ably.lib.realtime.AblyRealtime;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
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
@ImportRuntimeHints(NotificationEventIntegrationConfiguration.Hints.class)
class NotificationEventIntegrationConfiguration {

	@Bean
	ApplicationEventListeningMessageProducer applicationEventListeningMessageProducer() {
		var applicationEventListeningMessageProducer = new ApplicationEventListeningMessageProducer();
		applicationEventListeningMessageProducer.setEventTypes(NotificationEvent.class);
		return applicationEventListeningMessageProducer;
	}

	/*
	 *
	 * private final Logger log = LoggerFactory.getLogger(getClass());
	 *
	 * @Bean Channel notificationsChannel(AblyRealtime ablyRealtime, MogulService
	 * mogulService) { var pfb = new ProxyFactoryBean();
	 * pfb.setTargetClass(Channel.class); pfb.setProxyTargetClass(true);
	 * pfb.addAdvice((MethodInterceptor) _ -> { var channelName =
	 * AblyNotificationsUtils.ablyNoticationsChannelFor(mogulService.getCurrentMogul());
	 * log.debug("resolving notification channel name {}", channelName); return
	 * ablyRealtime.channels.get(channelName); }); return (Channel) pfb.getObject(); }
	 *
	 */

	@Bean
	AblyMessageHandler ablyMessageHandler(AblyRealtime ablyRealtime) {
		return new AblyMessageHandler(ablyRealtime);
	}

	@Bean
	IntegrationFlow notificationEventsToAblyOutbundIntegrationFlow(ApplicationEventListeningMessageProducer aemp,
			AblyMessageHandler messageHandler) {
		return IntegrationFlow //
			.from(aemp) //
			.filter(source -> source instanceof NotificationEvent)
			.transform(new AbstractTransformer() {
				@Override
				protected Object doTransform(Message<?> message) {
					var notificationEvent = (NotificationEvent) message.getPayload();
					var json = JsonUtils.write(notificationEvent);
					var topic = AblyNotificationsUtils.ablyNoticationsChannelFor(notificationEvent.mogulId());
					return MessageBuilder //
						.withPayload(json) //
						.setHeader(AblyHeaders.ABLY_NAME, notificationEvent.category())//
						.setHeader(AblyHeaders.ABLY_CHANNEL_NAME, topic)//
						.build();
				}
			})//
			.handle(messageHandler)//
			.get();
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			hints.reflection().registerType(NotificationEvent.class, MemberCategory.values());
		}

	}

}
