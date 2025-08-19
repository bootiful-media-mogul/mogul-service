package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableRepository;
import com.joshlong.mogul.api.Transcript;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Transactional
@SuppressWarnings("unchecked")
class DefaultTranscriptService implements TranscriptService {

	private final JdbcClient db;

	private final TranscriptRowMapper transcribableRowMapper;

	private final Map<String, TranscribableRepository<?>> repositories = new ConcurrentHashMap<>();

	private final MessageChannel requests;

	DefaultTranscriptService(TranscriptRowMapper transcribableRowMapper, JdbcClient db,
			Map<String, TranscribableRepository<?>> repositories, MessageChannel requests) {
		this.transcribableRowMapper = transcribableRowMapper;
		this.db = db;
		this.requests = requests;
		this.repositories.putAll(repositories);
	}

	private static String classNameFor(Transcribable transcribable) {
		return transcribable.getClass().getName();
	}

	private Transcript readThroughTranscriptionByKey(String clazz, String payloadKeyAsJson) {
		// todo some sort of caching.
		return CollectionUtils
			.firstOrNull(this.db.sql("select * from transcript where payload_class = ? and payload = ?")
				.params(clazz, payloadKeyAsJson)
				.query(this.transcribableRowMapper)
				.list());
	}

	@Override
	public <T extends Transcribable> T transcribable(Long transcribableId, Class<T> transcribableClass) {
		var repo = this.repositoryFor(transcribableClass);
		return repo.find(transcribableId);
	}

	@Override
	public Transcript transcript(Long mogulId, Transcribable payload) {
		var clazz = classNameFor(payload);
		var payloadKeyAsJson = JsonUtils.write(payload.transcribableId());
		var transcript = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		if (null == transcript) {
			db //
				.sql("insert into transcript(mogul_id,payload, payload_class) values (?,?,?)") //
				.params(mogulId, payloadKeyAsJson, clazz) //
				.update();
			transcript = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		}
		return transcript;
	}

	@Override
	public Transcript transcriptById(Long id) {
		var transcripts = this.db.sql("select * from transcript where id = ?")
			.params(id)
			.query(this.transcribableRowMapper)
			.list();
		return CollectionUtils.firstOrNull(transcripts);
	}

	@Override
	public void transcribe(Long mogulId, Long transcriptId, Map<String, Object> context) {
		var transcribable = this.transcribableFor(transcriptId);
		this.transcribe(mogulId, transcribable, context);
	}

	@Override
	public void transcribe(Long mogulId, Transcribable payload) {
		var ctx = this.repositoryFor(payload.getClass()).defaultContext(payload.transcribableId());
		this.transcribe(mogulId, payload, ctx);
	}

	private Long keyFor(Transcript transcript) {
		return JsonUtils.read(transcript.payload(), Long.class);
	}

	@Override
	public void transcribe(Long mogulId, Long transcriptId) {
		var transcript = this.transcriptById(transcriptId);
		var ctx = this.repositoryFor(transcript.payloadClass()).defaultContext(this.keyFor(transcript));
		this.transcribe(mogulId, transcriptId, ctx);
	}

	@Override
	public void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context) {
		var transcript = this.transcript(mogulId, payload);
		var transcribableKey = this.keyFor(transcript);
		var defaultContext = this.repositoryFor(payload.getClass()).defaultContext(transcribableKey);
		var finalMap = new HashMap<String, Object>();
		finalMap.putAll(defaultContext);
		finalMap.putAll(context);
		var message = MessageBuilder //
			.withPayload(new TranscriptionRequest(mogulId, payload, finalMap)) //
			.build();
		this.requests.send(message);
	}

	private Transcribable transcribableFor(Long transcriptId) {
		var transcript = this.transcriptById(transcriptId);
		var repo = this.repositoryFor((transcript.payloadClass()));
		return repo.find(JsonUtils.read(transcript.payload(), Long.class));
	}

	@Override
	public void writeTranscript(Transcribable transcribable, String transcript) {
		var payloadKeyAsJson = JsonUtils.write(transcribable.transcribableId());
		var clazz = classNameFor(transcribable);
		var transcriptObject = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		this.writeTranscript(transcriptObject.id(), transcript);
	}

	@Override
	public void writeTranscript(Long transcriptId, String transcript) {
		this.db.sql("update transcript set transcript = ? where  id = ? ").params(transcript, transcriptId).update();
	}

	@Override
	public <T extends Transcribable> String readTranscript(Long mogulId, T transcribable) {
		var transcript = this.transcript(mogulId, transcribable);
		return transcript.transcript();
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
	void transcriptInvalidatedEvent(TranscriptInvalidatedEvent event) {
		var repository = this.repositoryFor(event.type());
		var payload = repository.find(event.key());
		this.transcribe(event.mogulId(), payload, event.context());
	}

	@ApplicationModuleListener
	void recordCompletedTranscript(TranscriptCompletedEvent event) {
		var aClass = (Class<? extends Transcribable>) (event.type());
		var transcribableRepository = this.repositoryFor(aClass);
		var transcribable = transcribableRepository.find(event.transcribableId());
		this.writeTranscript(transcribable, event.text());
		var ctx = JsonUtils.write(Map.of("transcript", event.text(), "transcriptId", event.transcriptId()));
		var notificationEvent = NotificationEvent.systemNotificationEventFor(event.mogulId(), event,
				event.transcribableId().toString(), ctx);
		NotificationEvents.notify(notificationEvent);
	}

}
