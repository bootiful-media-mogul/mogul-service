package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableRepository;
import com.joshlong.mogul.api.transcripts.audio.Transcriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.PublishSubscribeChannelSpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
class TranscriptConfiguration {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Bean
	DefaultTranscriptService defaultTranscriptService(JdbcClient db,
			Map<String, TranscribableRepository<?>> repositories, @TranscriptMessageChannel MessageChannel in) {
		return new DefaultTranscriptService(transcriptRowMapper(), db, repositories, in);
	}

	@Bean
	IntegrationFlow transcriptIntegrationFlow(ApplicationEventPublisher publisher,
			@TranscriptMessageChannel MessageChannel inbound, TranscriptService transcriptService,
			Transcriber transcriber, TransactionTemplate tx) {
		return IntegrationFlow //
			.from(inbound) //
			.handle((GenericHandler<TranscriptionRequest>) (payload, headers) -> {
				this.log.debug("received a transcript request for mogul# {}, context: {}, transcribable# {}",
						payload.mogulId(), payload.context(), payload.payload().transcribableId());
				var transcribable = payload.payload();
				var mogulId = payload.mogulId();
				var transcript = transcriptService.transcript(payload.mogulId(), transcribable);
				var clazz = (Class<? extends Transcribable>) transcript.payloadClass();
				var transcribableId = transcribable.transcribableId();
				this.publishInTransaction(publisher, tx,
						new TranscriptionStartedEvent(mogulId, transcribableId, transcript.id(), clazz));
				var repository = transcriptService.repositoryFor(clazz);
				var audio = repository.audio(transcribableId);
				var content = transcriber.transcribe(audio);
				this.publishInTransaction(publisher, tx,
						new TranscriptCompletedEvent(mogulId, transcribableId, transcript.id(), clazz, content));
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
	@TranscriptMessageChannel
	PublishSubscribeChannelSpec<?> transcriptMessageChannel(SimpleAsyncTaskScheduler taskScheduler) {
		return MessageChannels.publishSubscribe(taskScheduler);
	}

	@Bean
	TranscriptRowMapper transcriptRowMapper() {
		return new TranscriptRowMapper();
	}

}
