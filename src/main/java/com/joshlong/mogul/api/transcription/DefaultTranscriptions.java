package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.Transcription;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Transactional
@SuppressWarnings("unchecked")
class DefaultTranscriptions implements Transcriptions {

	private final JdbcClient db;

	private final TranscriptionRowMapper transcribableRowMapper;

	private final Map<String, TranscribableRepository<?>> repositories = new ConcurrentHashMap<>();

	private final MessageChannel requests;

	DefaultTranscriptions(TranscriptionRowMapper transcribableRowMapper, JdbcClient db,
			Map<String, TranscribableRepository<?>> repositories, MessageChannel requests) {
		this.transcribableRowMapper = transcribableRowMapper;
		this.db = db;
		this.requests = requests;
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
	public void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context) {
		this.requests.send(MessageBuilder.withPayload(new TranscriptionRequest(mogulId, payload, context)).build());
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
		var aClass = (Class<? extends Transcribable>) (event.type());
		var transcribableRepository = this.repositoryFor(aClass);
		transcribableRepository.write(event.key(), event.text());
	}

}
