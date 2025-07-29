package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.Transcription;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.event.inbound.ApplicationEventListeningMessageProducer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
@SuppressWarnings("unchecked")
class DefaultTranscriptionService implements TranscriptionService {

	private final JdbcClient db;

	private final TranscriptionRowMapper transcribableRowMapper;

	private final Map<String, TranscribableRepository<?>> repositories = new ConcurrentHashMap<>();

	private final ApplicationEventPublisher publisher;

	DefaultTranscriptionService(TranscriptionRowMapper transcribableRowMapper, JdbcClient db,
			ApplicationEventPublisher publisher, Map<String, TranscribableRepository<?>> repositories) {
		this.transcribableRowMapper = transcribableRowMapper;
		this.db = db;
		this.publisher = publisher;
		this.repositories.putAll(repositories);
	}

	private Transcription readThroughTranscriptionByKey(String clazz, String payloadKeyAsJson) {
		// todo some sort of caching.
		return CollectionUtils
			.firstOrNull(this.db.sql("select * from transcription where payload_class = ? and payload = ?")
				.params(clazz, payloadKeyAsJson)
				.query(this.transcribableRowMapper)
				.list());
	}

	@Override
	public Transcription transcription(Transcribable payload) {
		var clazz = payload.getClass().getName();
		var payloadKeyAsJson = JsonUtils.write(payload.transcriptionKey());
		var transcription = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		if (null == transcription) {
			db //
				.sql("insert into transcription(payload, payload_class) values (?,?)") //
				.params(payloadKeyAsJson, clazz) //
				.update();
			transcription = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		}
		return transcription;
	}

	@Override
	public void transcribe(Transcribable payload) {
		this.publisher
			.publishEvent(new TranscriptionStartedEvent(payload.transcriptionKey(), payload.getClass().getName()));
	}

	@Override
	public <T extends Transcribable> TranscribableRepository<T> repositoryFor(Class<T> clazz) {
		for (var repository : this.repositories.values()) {
			if (repository.supports(clazz)) {
				return (TranscribableRepository<T>) repository;
			}
		}
		throw new IllegalStateException(
				"there's no " + TranscribableRepository.class.getName() + " for " + clazz.getName());
	}

	@ApplicationModuleListener
	void record(TranscriptionCompletedEvent event) {
		var aClass = (Class<? extends Transcribable>) ReflectionUtils.classForName(event.type());
		var transcribableRepository = this.repositoryFor(aClass);
		transcribableRepository.write(event.key(), event.text());
	}

}

record TranscriptionStartedEvent(Long key, String type) {
}

record TranscriptionCompletedEvent(Long key, String type, String text) {
}

/**
 * we do this in a Spring Integration flow so that the requests are serialized and that no
 * more than one segment is being transcribed at a time. Hopefully this'll improve
 * scalability.
 */
@Configuration
@SuppressWarnings("unchecked")
class TranscriptionIntegrationFlowConfiguration {

	@Bean
	IntegrationFlow integrationFlow(TransactionTemplate transactionTemplate, ApplicationEventPublisher publisher,
			TranscriptionService transcriptionService, Transcriber transcriber) {
		var ae = new ApplicationEventListeningMessageProducer();
		ae.setEventTypes(TranscriptionStartedEvent.class);
		return IntegrationFlow.from(ae) //
			.handle((GenericHandler<TranscriptionStartedEvent>) (payload, _) -> { //
				var clazz = (Class<? extends Transcribable>) ReflectionUtils.classForName(payload.type());
				var repository = transcriptionService.repositoryFor(clazz);
				var key = payload.key();
				var audio = repository.audio(key);
				var txtContent = transcriber.transcribe(audio);
				transactionTemplate.execute(_ -> {
					publisher.publishEvent(new TranscriptionCompletedEvent(key, payload.type(), txtContent));
					return null;
				});
				return null;
			}) //
			.get();
	}

}