package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableRepository;
import com.joshlong.mogul.api.Transcription;
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
class DefaultTranscriptionService implements TranscriptionService {

	private final JdbcClient db;

	private final TranscriptionRowMapper transcribableRowMapper;

	private final Map<String, TranscribableRepository<?>> repositories = new ConcurrentHashMap<>();

	private final MessageChannel requests;

	DefaultTranscriptionService(TranscriptionRowMapper transcribableRowMapper, JdbcClient db,
			Map<String, TranscribableRepository<?>> repositories, MessageChannel requests) {
		this.transcribableRowMapper = transcribableRowMapper;
		this.db = db;
		this.requests = requests;
		this.repositories.putAll(repositories);
	}

	private static String classNameFor(Transcribable transcribable) {
		return transcribable.getClass().getName();
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
	public Transcription transcription(Long mogulId, Transcribable payload) {
		var clazz = classNameFor(payload);
		var payloadKeyAsJson = JsonUtils.write(payload.transcribableId());
		var transcription = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		if (null == transcription) {
			db //
				.sql("insert into transcription(mogul_id,payload, payload_class) values (?,?,?)") //
				.params(mogulId, payloadKeyAsJson, clazz) //
				.update();
			transcription = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		}
		return transcription;
	}

	@Override
	public Transcription transcriptionById(Long id) {
		var transcriptions = this.db.sql("select * from transcription where id = ?")
			.params(id)
			.query(this.transcribableRowMapper)
			.list();
		return CollectionUtils.firstOrNull(transcriptions);
	}

	@Override
	public void transcribe(Long mogulId, Long transcriptionId, Map<String, Object> context) {
		var transcribable = this.transcribableFor(transcriptionId);
		this.transcribe(mogulId, transcribable, context);
	}

	@Override
	public void transcribe(Long mogulId, Transcribable payload) {
		var ctx = this.repositoryFor(payload.getClass()).defaultContext(payload.transcribableId());
		this.transcribe(mogulId, payload, ctx);
	}

	private Long keyFor(Transcription transcription) {
		return JsonUtils.read(transcription.payload(), Long.class);
	}

	@Override
	public void transcribe(Long mogulId, Long transcriptionId) {
		var transcription = this.transcriptionById(transcriptionId);
		var ctx = this.repositoryFor(transcription.payloadClass()).defaultContext(this.keyFor(transcription));
		this.transcribe(mogulId, transcriptionId, ctx);
	}

	@Override
	public void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context) {
		var transcription = this.transcription(mogulId, payload);
		var transcribableKey = this.keyFor(transcription);
		var defaultContext = this.repositoryFor(payload.getClass()).defaultContext(transcribableKey);
		var finalMap = new HashMap<String, Object>();
		finalMap.putAll(defaultContext);
		finalMap.putAll(context);
		var message = MessageBuilder //
			.withPayload(new TranscriptionRequest(mogulId, payload, finalMap)) //
			.build();
		this.requests.send(message);
	}

	private Transcribable transcribableFor(Long transcriptionId) {
		var transcription = this.transcriptionById(transcriptionId);
		var repo = this.repositoryFor((transcription.payloadClass()));
		return repo.find(JsonUtils.read(transcription.payload(), Long.class));
	}

	@Override
	public void writeTranscript(Transcribable transcribable, String transcript) {
		var payloadKeyAsJson = JsonUtils.write(transcribable.transcribableId());
		var clazz = classNameFor(transcribable);
		var transcription = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		this.writeTranscript(transcription.id(), transcript);
	}

	@Override
	public void writeTranscript(Long transcriptionId, String transcript) {
		this.db.sql("update transcription set transcript = ? where  id = ? ")
			.params(transcript, transcriptionId)
			.update();
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
	void transcriptionInvalidatedEvent(TranscriptionInvalidatedEvent event) {
		var repository = this.repositoryFor(event.type());
		var payload = repository.find(event.key());
		this.transcribe(event.mogulId(), payload, event.context());
	}

	@ApplicationModuleListener
	void recordCompletedTranscript(TranscriptionCompletedEvent event) {
		var aClass = (Class<? extends Transcribable>) (event.type());
		var transcribableRepository = this.repositoryFor(aClass);
		var transcribable = transcribableRepository.find(event.transcribableId());
		this.writeTranscript(transcribable, event.text());
		var ctx = JsonUtils.write(Map.of("transcript", event.text(), "transcriptionId", event.transcriptionId()));
		var notificationEvent = NotificationEvent.systemNotificationEventFor(event.mogulId(), event,
				event.transcribableId().toString(), ctx);
		NotificationEvents.notify(notificationEvent);
	}

}
