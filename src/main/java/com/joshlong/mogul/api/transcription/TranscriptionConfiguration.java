package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableRepository;
import com.joshlong.mogul.api.transcription.audio.Transcriber;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.PublishSubscribeChannelSpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
class TranscriptionConfiguration {

	private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

	@Bean
	DefaultTranscriptionService defaultTranscriptions(JdbcClient db,
			Map<String, TranscribableRepository<?>> repositories, @TranscriptionMessageChannel MessageChannel in) {
		return new DefaultTranscriptionService(transcriptionRowMapper(), db, repositories, in);
	}

	@Bean
	IntegrationFlow transcriptionIntegrationFlow(ApplicationEventPublisher publisher,
			@TranscriptionMessageChannel MessageChannel inbound, TranscriptionService transcriptionService,
			Transcriber transcriber, TransactionTemplate tx) {
		return IntegrationFlow //
			.from(inbound) //
			.handle((GenericHandler<TranscriptionRequest>) (payload, headers) -> {
				var transcribable = payload.payload();
				var mogulId = payload.mogulId();
				var transcription = transcriptionService.transcription(transcribable);
				var clazz = (Class<? extends Transcribable>) transcription.payloadClass();
				this.publishInTransaction(publisher, tx,
						new TranscriptionStartedEvent(mogulId, transcribable.transcriptionKey(), clazz));
				var repository = transcriptionService.repositoryFor(clazz);
				var audio = repository.audio(transcribable.transcriptionKey());
				var content = transcriber.transcribe(audio);
				this.publishInTransaction(publisher, tx,
						new TranscriptionCompletedEvent(mogulId, transcribable.transcriptionKey(), clazz, content));
				return null;
			}) //
			.get();
	}

	private void publishInTransaction(ApplicationEventPublisher publisher, TransactionTemplate transactionTemplate,
			Object eventObject) {
		transactionTemplate.execute(_ -> {
			publisher.publishEvent(eventObject);
			return null;
		});
	}

	@Bean
	@TranscriptionMessageChannel
	PublishSubscribeChannelSpec<?> transcriptionRequests() {
		return MessageChannels.publishSubscribe(this.executor);
	}

	@Bean
	TranscriptionRowMapper transcriptionRowMapper() {
		return new TranscriptionRowMapper();
	}

}
