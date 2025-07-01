package com.joshlong.mogul.api.notifications.ably;

import com.joshlong.mogul.api.ApiProperties;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.ably.integration.AblyHeaders;
import com.joshlong.mogul.api.notifications.ably.integration.AblyMessageHandler;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
//
//    @Bean
//    IntegrationFlow ablyOutboundFlow(Channel channel) {
//        var ctr = new AtomicInteger(0);
//        return IntegrationFlow //
//                .from(() -> {
//                    if (ctr.incrementAndGet() == 1) {
//                        log.info("sending a message to Ably");
//                        return MessageBuilder //
//                                .withPayload((Object) ("hello, Spring Integration @ " + Instant.now())) //
//                                .setHeader(AblyHeaders.ABLY_NAME, "test")//
//                                .build();
//                    }
//                    return null;
//                }) //
//                .handle(new AblyOutboundMessageHandler(channel))//
//                .get();
//    }

//    @Bean
//    ApplicationEventListeningMessageProducer applicationEventListeningMessageProducer() {
//        var aemp = new ApplicationEventListeningMessageProducer();
//        aemp.setEventTypes(NotificationEvent.class);
//        return aemp;
//    }
//

//    @Bean
//    IntegrationFlow notificationEventsToAblyOutbundIntegrationFlow(ApplicationEventListeningMessageProducer aemp) {
//        return IntegrationFlow
//                .from(aemp)
//                .handle((GenericHandler<NotificationEvent>) (payload, headers) -> {
//                    System.out.println("got the notification event " + payload + " with headers " + headers);
//                    return null;
//                })
//                .get();
//    }

//    @Bean
//    AblyMessageListeningMessageProducer ablyMessageListeningMessageProducer(Channel channel) {
//        return new AblyMessageListeningMessageProducer(channel);
//    }

    @Bean
    ApplicationEventListeningMessageProducer applicationEventListeningMessageProducer() {
        var aelm = new ApplicationEventListeningMessageProducer();
        aelm.setEventTypes(NotificationEvent.class);
        return aelm;
    }

    @Bean
    AblyMessageHandler messageHandler(Channel channel) {
        return new AblyMessageHandler(channel);
    }

    // i have a hackney'd version of the code that publishes messages to a named topic
    // i am able to publish a NotificationEvent and then, in an integration flow, see it call the outbound support for Ably
    // so now we'd test it in javascript, make sure its using the short lived token strategy (maybe all of this gets turned
    // into javascript at some point?)
    @Bean
    IntegrationFlow notificationEventsToAblyOutbundIntegrationFlow(
            AblyMessageHandler messageHandler,
            ApplicationEventListeningMessageProducer aemp) {
        return IntegrationFlow
                .from(aemp) //
                .transform(new AbstractTransformer() {
                    @Override
                    protected Object doTransform(Message<?> message) {
                        if (message.getPayload() instanceof NotificationEvent notificationEvent) {
                            return MessageBuilder
                                    .withPayload(notificationEvent.mogulId() + ':' + notificationEvent.key())
                                    .setHeader(AblyHeaders.ABLY_NAME, noticationsDestination(notificationEvent.mogulId()))
                                    .build();
                        }
                        return null;
                    }
                })//
                //.transform(NotificationEvent.class, source -> source.mogulId() + ":" +source.key())
                .handle(messageHandler)//
                .get();
    }

    private static String noticationsDestination(Long mogulId) {
        return "notifications-" + mogulId;
    }

//    // let's see if we can't get the test events to route to this
//    @Bean
//    IntegrationFlow ablyInboundFlow() {
//        return IntegrationFlow //
//                .from(mps) //
//                .handle((payload, headers) -> { //
//                    log.info("received a file [{}] from Ably, with headers {}", payload, headers.entrySet());
//                    return null;
//                })//
//                .get();
//    }


}
