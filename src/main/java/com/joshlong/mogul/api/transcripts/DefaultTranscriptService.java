package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.AbstractDomainService;
import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableResolver;
import com.joshlong.mogul.api.Transcript;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Transactional
@SuppressWarnings("unchecked")
class DefaultTranscriptService extends AbstractDomainService<Transcribable, TranscribableResolver<?>>
		implements TranscriptService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final JdbcClient db;

	private final TranscriptRowMapper transcribableRowMapper;

	private final ApplicationEventPublisher publisher;

	private final MessageChannel requests;

	DefaultTranscriptService(TranscriptRowMapper transcribableRowMapper, JdbcClient db,
			Collection<TranscribableResolver<?>> resolvers, ApplicationEventPublisher publisher,
			MessageChannel requests) {
		super(resolvers);
		this.transcribableRowMapper = transcribableRowMapper;
		this.db = db;
		this.publisher = publisher;
		this.requests = requests;
	}

	private static String classNameFor(Transcribable transcribable) {
		return transcribable.getClass().getName();
	}

	private static <T extends Transcribable> Map<TranscriptKey, Transcribable> keyBy(Collection<T> payloads) {
		var keyed = new LinkedHashMap<TranscriptKey, Transcribable>();
		for (var payload : payloads)
			keyed.put(new TranscriptKey(classNameFor(payload), JsonUtils.write(payload.transcribableId())), payload);
		return keyed;
	}

	private Transcript readThroughTranscriptionByKey(String clazz, String payloadKeyAsJson) {
		return CollectionUtils
			.firstOrNull(this.db.sql("select * from transcript where payload_class = ? and payload = ?")
				.params(clazz, payloadKeyAsJson)
				.query(this.transcribableRowMapper)
				.list());
	}

	@Override
	public <T extends Transcribable> T transcribable(Long transcribableId, Class<T> transcribableClass) {
		var repo = this.resolverFor(transcribableClass);
		return repo.find(transcribableId);
	}

	@Override
	public Transcript transcriptById(Long id) {
		var transcripts = this.db //
			.sql("select * from transcript where id = ?")//
			.params(id)//
			.query(this.transcribableRowMapper) //
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
		var ctx = this.resolverFor(payload.getClass()).defaultContext(payload.transcribableId());
		this.transcribe(mogulId, payload, ctx);
	}

	private Long keyFor(Transcript transcript) {
		return JsonUtils.read(transcript.payload(), Long.class);
	}

	@Override
	public void transcribe(Long mogulId, Long transcriptId) {
		var transcript = this.transcriptById(transcriptId);
		var ctx = this.resolverFor(transcript.payloadClass()).defaultContext(this.keyFor(transcript));
		this.transcribe(mogulId, transcriptId, ctx);
	}

	@Override
	public void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context) {
		var transcript = this.transcript(mogulId, payload);
		var transcribableKey = this.keyFor(transcript);
		var defaultContext = this.resolverFor(payload.getClass()).defaultContext(transcribableKey);
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
		var repo = this.resolverFor((transcript.payloadClass()));
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
		this.db //
			.sql("update transcript set transcript = ? where  id = ? ") //
			.params(transcript, transcriptId) //
			.update();
	}

	@Override
	public Transcript transcript(Long mogulId, Transcribable payload) {
		var clazz = classNameFor(payload);
		var payloadKeyAsJson = JsonUtils.write(payload.transcribableId());
		var transcript = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		if (null == transcript) {
			this.db //
				.sql("insert into transcript(mogul_id,payload, payload_class) values (?,?,?)") //
				.params(mogulId, payloadKeyAsJson, clazz) //
				.update();
			transcript = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		}
		return transcript;
	}

	@Override
	public <T extends Transcribable> Map<Transcribable, String> readTranscripts(Long mogulId, Collection<T> toRead) {
		if (toRead.isEmpty())
			return Map.of();
		var keyed = keyBy(toRead);
		var found = this.transcriptsFor(mogulId, keyed.keySet());
		var map = new HashMap<Transcribable, String>();
		keyed.forEach((key, transcribable) -> {
			var transcript = found.get(key);
			if (transcript != null)
				map.put(transcribable, transcript.transcript());
		});
		return map;
	}

	@Override
	public <T extends Transcribable> Map<Transcribable, Transcript> transcripts(Long mogulId, Collection<T> payloads) {
		if (payloads.isEmpty())
			return Map.of();
		var keyed = keyBy(payloads);
		// deliberately not filtered by mogul, to match the single-payload
		// transcript(Long, Transcribable) above: filtering here would hide an existing
		// row and make us insert a duplicate alongside it.
		var found = this.transcriptsFor(null, keyed.keySet());
		var missing = keyed.keySet().stream().filter(key -> !found.containsKey(key)).toList();
		if (!missing.isEmpty()) {
			// one insert for everything that had no row yet, rather than one apiece.
			// transcript has no unique constraint to conflict on, so the rows we write
			// are exactly the ones the read above didn't find.
			var sql = new StringBuilder("insert into transcript(mogul_id, payload, payload_class) values ");
			var params = new ArrayList<>();
			for (var i = 0; i < missing.size(); i++) {
				sql.append(i == 0 ? "" : ",").append("(?,?,?)");
				params.add(mogulId);
				params.add(missing.get(i).payload());
				params.add(missing.get(i).payloadClass());
			}
			this.db.sql(sql.toString()).params(params.toArray()).update();
			found.putAll(this.transcriptsFor(null, missing));
		}
		var results = new LinkedHashMap<Transcribable, Transcript>();
		keyed.forEach((key, transcribable) -> results.put(transcribable, found.get(key)));
		return results;
	}

	/**
	 * reads a batch of transcripts in one query per distinct payload class -- in practice
	 * one, since a batch is a list of the same kind of thing. matching on payload alone
	 * would let two Transcribables of different types that happen to share an id resolve
	 * to each other's transcript, which is why the class is part of both the query and
	 * the key.
	 */
	private Map<TranscriptKey, Transcript> transcriptsFor(Long mogulId, Collection<TranscriptKey> keys) {
		var byClass = keys.stream()
			.collect(Collectors.groupingBy(TranscriptKey::payloadClass,
					Collectors.mapping(TranscriptKey::payload, Collectors.toSet())));
		var results = new HashMap<TranscriptKey, Transcript>();
		for (var entry : byClass.entrySet()) {
			var payloads = new SqlArrayValue("text", entry.getValue().toArray(String[]::new));
			var transcripts = (null == mogulId) //
					? this.db.sql("select * from transcript where payload_class = ? and payload = any(?)")
						.params(entry.getKey(), payloads)
						.query(this.transcribableRowMapper)
						.list()
					: this.db
						.sql("select * from transcript where payload_class = ? and payload = any(?) and mogul_id = ?")
						.params(entry.getKey(), payloads, mogulId)
						.query(this.transcribableRowMapper)
						.list();
			for (var transcript : transcripts)
				results.put(new TranscriptKey(transcript.payloadClass().getName(), transcript.payload()), transcript);
		}
		return results;
	}

	@Override
	public <T extends Transcribable> TranscribableResolver<T> resolverFor(Class<T> clazz) {
		return (TranscribableResolver<T>) this.findResolver(clazz);
	}

	@ApplicationModuleListener
	void transcriptInvalidatedEvent(TranscriptInvalidatedEvent event) {
		var repository = this.resolverFor(event.type());
		var payload = repository.find(event.key());
		this.transcribe(event.mogulId(), payload, event.context());
	}

	// todo delete transcripts when the podcast and the segments to which it belongs is
	// deleted
	@ApplicationModuleListener
	void recordCompletedTranscript(TranscriptCompletedEvent event) {
		this.recordTranscript(event);
		this.notifyClient(event);
	}

	private void recordTranscript(TranscriptCompletedEvent event) {
		var aClass = (Class<? extends Transcribable>) (event.type());
		var transcribableRepository = this.resolverFor(aClass);
		var transcribable = transcribableRepository.find(event.transcribableId());
		this.writeTranscript(transcribable, event.text());
		this.publisher
			.publishEvent(new TranscriptRecordedEvent(event.mogulId(), event.transcribableId(), event.type()));
	}

	private void notifyClient(TranscriptCompletedEvent event) {
		var ctx = JsonUtils.write(Map.of("transcript", event.text(), "transcriptId", event.transcriptId()));
		var notificationEvent = NotificationEvent //
			.systemNotificationEventFor(event.mogulId(), event, event.transcribableId().toString(), ctx);
		NotificationEvents.notify(notificationEvent);
	}

	private record TranscriptKey(String payloadClass, String payload) {
	}

}
