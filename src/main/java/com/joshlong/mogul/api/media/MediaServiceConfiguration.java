package com.joshlong.mogul.api.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.PublishSubscribeChannelSpec;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * handles normalizing media like audio and images. delegates, ultimately, to
 * {@link Normalization}. Publishes a {@link MediaNormalizedEvent mediaNormalizedEvent} on
 * success, containing the affected {@link com.joshlong.mogul.api.managedfiles.ManagedFile
 * managedFiles}.
 */
@Configuration
class MediaServiceConfiguration {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

	@Bean
	DefaultMediaService mediaService(@MediaNormalizationMessageChannel MessageChannel channel) {
		return new DefaultMediaService(channel);
	}

	@Bean
	@MediaNormalizationMessageChannel
	PublishSubscribeChannelSpec<?> mediaNormalizationRequests() {
		return MessageChannels.publishSubscribe(executor);
	}

	@Bean
	IntegrationFlow mediaNormalizationIntegrationFlow( //
			@MediaNormalizationMessageChannel MessageChannel inbound, //
			ApplicationEventPublisher publisher, TransactionTemplate transactionTemplate, //
			Normalization normalization) { //
		return IntegrationFlow //
			.from(inbound) //
			.handle(MediaNormalizationRequest.class, (payload, _) -> { //
				try {
					normalization.normalize(payload.in(), payload.out());
					transactionTemplate.execute(_ -> {
						publisher
							.publishEvent(new MediaNormalizedEvent(payload.in(), payload.out(), payload.context()));
						return null;
					});
					log.debug("media normalization completed for {} to {}", payload.in().id(), payload.out().id());
				} //
				catch (Exception e) {
					throw new RuntimeException(e);
				}
				return null;
			}) //
			.

			get();
	}

}
