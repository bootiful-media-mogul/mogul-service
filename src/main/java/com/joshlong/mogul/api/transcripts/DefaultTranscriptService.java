package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.AbstractDomainService;
import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableResolver;
import com.joshlong.mogul.api.Transcript;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.processors.ProcessorCompletedEvent;
import com.joshlong.mogul.api.processors.Processors;
import com.joshlong.mogul.utils.CollectionUtils;
import com.joshlong.mogul.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Transactional
@SuppressWarnings("unchecked")
class DefaultTranscriptService extends AbstractDomainService<Transcribable, TranscribableResolver<?>>
		implements TranscriptService {

	/**
	 * the name of the {@code Processor} bean over in the processors module, which is also
	 * the name a reply for us comes back under.
	 */
	static final String PROCESSOR_ID = "audioTranscriptionProcessor";

	// what the processor reads
	private static final String INPUT_BUCKET = "inputBucket";

	private static final String INPUT_KEY = "inputKey";

	// what it writes back
	private static final String TRANSCRIPT = "transcript";

	// what it never looks at, and hands back untouched: our own return address. the
	// type is not among them -- the transcript row records the class it is for, and
	// re-reading it beats a second copy on the wire that can disagree with the first.
	private static final String MOGUL_ID = "mogulId";

	private static final String TRANSCRIBABLE_ID = "transcribableId";

	private static final String TRANSCRIPT_ID = "transcriptId";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final JdbcClient db;

	private final TranscriptRowMapper transcribableRowMapper;

	private final ApplicationEventPublisher publisher;

	private final Processors processors;

	private final TransactionTemplate transactionTemplate;

	DefaultTranscriptService(TranscriptRowMapper transcribableRowMapper, JdbcClient db,
			Collection<TranscribableResolver<?>> resolvers, ApplicationEventPublisher publisher, Processors processors,
			TransactionTemplate transactionTemplate) {
		super(resolvers);
		this.transcribableRowMapper = transcribableRowMapper;
		this.db = db;
		this.publisher = publisher;
		this.processors = processors;
		this.transactionTemplate = transactionTemplate;
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

	static boolean alreadyDispatchedFor(String sourceEtag, Transcript existing) {
		return sourceEtag != null && existing != null && sourceEtag.equals(existing.sourceEtag());
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
		var clazz = transcript.payloadClass();
		var resolver = this.resolverFor(clazz);
		var audio = resolver.audio(transcribableKey);
		// a transcription of an object with nothing in it would come back empty and
		// overwrite whatever text is there, which is worse than not running.
		Assert.state(audio != null && audio.written(),
				() -> "there is no audio to transcribe for " + clazz.getName() + " #" + transcribableKey);
		var request = new HashMap<String, Object>();
		request.putAll(resolver.defaultContext(transcribableKey));
		request.putAll(context);
		request.put(INPUT_BUCKET, audio.bucket());
		request.put(INPUT_KEY, audio.key());
		request.put(MOGUL_ID, mogulId);
		request.put(TRANSCRIBABLE_ID, transcribableKey);
		request.put(TRANSCRIPT_ID, transcript.id());
		try {
			this.processors.process(PROCESSOR_ID, request);
		} //
		catch (Exception e) {
			throw new RuntimeException("could not request the transcription of transcript #" + transcript.id(), e);
		}
		this.publish(new TranscriptionStartedEvent(mogulId, transcribableKey, transcript.id(), clazz));
		this.log.debug("requested the transcription of {} #{} into transcript #{}", clazz.getName(), transcribableKey,
				transcript.id());
	}

	@ApplicationModuleListener
	void onProcessorCompleted(ProcessorCompletedEvent event) {
		if (!PROCESSOR_ID.equals(event.processorId()))
			return;
		var context = event.context();
		var mogulId = (Long) context.get(MOGUL_ID);
		var transcribableId = (Long) context.get(TRANSCRIBABLE_ID);
		var transcriptId = (Long) context.get(TRANSCRIPT_ID);
		var transcript = this.transcriptById(transcriptId);
		if (transcript == null) {
			// the row went away while the work was in flight -- the segment was deleted,
			// most likely. there is nothing left to write the text to.
			this.log.warn("transcript #{} no longer exists; dropping the reply for it", transcriptId);
			return;
		}
		var clazz = transcript.payloadClass();
		if (!event.success()) {
			var error = StringUtils.hasText(event.error()) ? event.error() : "no reason given";
			this.log.warn("the transcription of {} #{} failed: {}", clazz.getName(), transcribableId, error);
			// the etag was claimed before dispatch so that a duplicate delivery wouldn't
			// start a second transcription of the same bytes. nothing is in flight any
			// more, so release the claim -- otherwise this recording can never be
			// transcribed again, and the guard turns a transient failure into a
			// permanent one.
			this.db.sql("update transcript set source_etag = null where id = ?").params(transcriptId).update();
			var failed = new TranscriptionFailedEvent(mogulId, transcribableId, transcriptId, clazz, error);
			this.publish(failed);
			// visible, not merely delivered: the editor has a transcript panel disabled
			// and waiting on this, and nothing else is going to tell the mogul why the
			// text never showed up.
			NotificationEvents.notifyAsync(NotificationEvent.visibleNotificationEventFor(mogulId, failed,
					transcribableId.toString(), JsonUtils.write(Map.of("transcriptId", transcriptId))));
			return;
		}
		var text = context.get(TRANSCRIPT) instanceof String s ? s : "";
		this.publish(new TranscriptCompletedEvent(mogulId, transcribableId, transcriptId, clazz, text));
	}

	private void publish(Object event) {
		this.transactionTemplate.executeWithoutResult(_ -> this.publisher.publishEvent(event));
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
		var sourceEtag = event.sourceEtag();
		if (sourceEtag != null) {
			// the audio that got us here arrives over an at-least-once queue, so this can
			// be the same recording announced twice. transcription is the most expensive
			// thing in the system -- a full ffmpeg decode plus the model calls -- and it
			// is worth one SELECT to find out we already started it for exactly these
			// bytes.
			var existing = this.transcript(event.mogulId(), payload);
			if (alreadyDispatchedFor(sourceEtag, existing)) {
				this.log.debug("already transcribing [{}] of {} #{}; not doing it twice", sourceEtag,
						event.type().getName(), event.key());
				return;
			}
			// claimed before dispatch, not after it completes: the duplicate delivery we
			// are guarding against arrives while the first transcription is still
			// running,
			// so waiting for a result to stamp would let it straight through.
			this.db.sql("update transcript set source_etag = ? where id = ?")
				.params(sourceEtag, existing.id())
				.update();
		}
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
