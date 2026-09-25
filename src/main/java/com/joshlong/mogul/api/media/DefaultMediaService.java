package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.processors.ProcessorCompletedEvent;
import com.joshlong.mogul.api.processors.Processors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * normalization is a request now, not a method call. this sends one, and -- some unknown
 * number of seconds later, on an AMQP listener thread, in a JVM that may have restarted
 * in between -- puts the answer back together into the {@link MediaNormalizedEvent} the
 * rest of the api has always waited for.
 * <p>
 * what crosses the wire is the smallest thing that will do: two buckets, two keys, a
 * filename and a content type. the {@link ManagedFile managedFiles} that those
 * coordinates came out of, and the podcast episode that wanted them, never leave this
 * module -- they are parked in {@code media_normalization} under the correlation id and
 * looked up again when the reply lands.
 */
class DefaultMediaService implements MediaService {

	/**
	 * the name of the {@code Processor} bean over in the processors module.
	 */
	static final String PROCESSOR_ID = "mediaNormalizationProcessor";

	// what we send it
	private static final String INPUT_BUCKET = "inputBucket";

	private static final String INPUT_KEY = "inputKey";

	private static final String INPUT_FILENAME = "inputFilename";

	private static final String INPUT_CONTENT_TYPE = "inputContentType";

	private static final String OUTPUT_BUCKET = "outputBucket";

	private static final String OUTPUT_KEY = "outputKey";

	// what it sends back
	private static final String OUTPUT_CONTENT_TYPE = "outputContentType";

	private static final String EXCEPTION = "exception";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Processors processors;

	private final ManagedFileService managedFileService;

	private final ApplicationEventPublisher publisher;

	private final JdbcClient db;

	private final JsonMapper jsonMapper;

	private final MediaNormalizationRowMapper rowMapper;

	private final TransactionTemplate transactionTemplate;

	private final TransactionTemplate newTransactionTemplate;

	DefaultMediaService(Processors processors, ManagedFileService managedFileService,
			ApplicationEventPublisher publisher, JdbcClient db, JsonMapper jsonMapper,
			TransactionTemplate transactionTemplate, TransactionTemplate newTransactionTemplate) {
		this.processors = processors;
		this.managedFileService = managedFileService;
		this.publisher = publisher;
		this.db = db;
		this.jsonMapper = jsonMapper;
		this.rowMapper = new MediaNormalizationRowMapper(jsonMapper);
		this.transactionTemplate = transactionTemplate;
		this.newTransactionTemplate = newTransactionTemplate;
	}

	@Override
	public void normalize(ManagedFile input, ManagedFile output, Map<String, Object> context) {
		if (!input.written()) {
			this.log.debug("the input file {} has not been written yet, so we can't normalize it", input.id());
			return;
		}
		var correlationId = UUID.randomUUID().toString();
		// in its own transaction, and therefore committed before the request goes out. a
		// small file can be normalized and replied to well inside the time it takes the
		// caller's transaction to commit, and a reply that finds no row is a reply that
		// gets dropped.
		this.newTransactionTemplate.executeWithoutResult(_ -> this.db.sql("""
				insert into media_normalization(correlation_id, input_managed_file_id, output_managed_file_id, context)
				values (?, ?, ?, ?)
				""") //
			.params(correlationId, input.id(), output.id(), this.jsonMapper.writeValueAsString(context)) //
			.update());
		try {
			this.processors.process(PROCESSOR_ID, correlationId, Map.of(//
					INPUT_BUCKET, input.bucket(), //
					INPUT_KEY, input.key(), //
					INPUT_FILENAME, input.filename(), //
					INPUT_CONTENT_TYPE, input.contentType(), //
					OUTPUT_BUCKET, output.bucket(), //
					OUTPUT_KEY, output.key() //
			));
			this.log.debug("requested the normalization of {} into {} as [{}]", input.id(), output.id(), correlationId);
		} //
		catch (Exception e) {
			throw new RuntimeException("could not request the normalization of ManagedFile #" + input.id(), e);
		}
	}

	@ApplicationModuleListener
	void onProcessorCompleted(ProcessorCompletedEvent event) {
		if (!PROCESSOR_ID.equals(event.processorId()))
			return;
		var correlationId = event.correlationId();
		var context = event.context() == null ? Map.<String, Object>of() : event.context();
		// AMQP is at-least-once, and normalization is not. whoever wins this update owns
		// the reply; a redelivery updates nothing and stops here.
		var claimed = this.db.sql("""
				update media_normalization
				set completed = now(), successful = ?, error = ?
				where correlation_id = ? and completed is null
				""") //
			.params(event.success(), (String) context.get(EXCEPTION), correlationId) //
			.update();
		if (claimed == 0) {
			this.log.debug("[{}] is not an outstanding media normalization. ignoring it.", correlationId);
			return;
		}
		var normalization = this.db.sql("select * from media_normalization where correlation_id = ?")
			.param(correlationId)
			.query(this.rowMapper)
			.single();
		if (!event.success()) {
			this.log.warn("the normalization of ManagedFile #{} into #{} failed: {}",
					normalization.inputManagedFileId(), normalization.outputManagedFileId(),
					context.getOrDefault(EXCEPTION, "no reason given"));
			return;
		}
		var in = this.managedFileService.getManagedFileById(normalization.inputManagedFileId());
		var output = this.managedFileService.getManagedFileById(normalization.outputManagedFileId());
		// the processor wrote the bytes straight into the output object, so there is
		// nothing to upload here -- only our record of that object to bring in line with
		// what is now actually in the bucket.
		this.managedFileService.refreshManagedFileFromStorage(output.id(), output.filename(),
				this.outputContentType(context, in));
		var merged = new HashMap<>(normalization.context());
		// the processor's findings -- duration, size, what it actually wrote -- go on top
		// of the caller's context, which is what MediaNormalizedEvent has always carried.
		merged.putAll(context);
		var out = this.managedFileService.getManagedFileById(output.id());
		this.transactionTemplate
			.executeWithoutResult(_ -> this.publisher.publishEvent(new MediaNormalizedEvent(in, out, merged)));
		this.log.debug("media normalization completed for {} into {}", in.id(), out.id());
	}

	/**
	 * what the processor says it wrote. if it didn't say, fall back to inferring it the
	 * way this used to be inferred locally: an image becomes a jpg, anything else becomes
	 * an mp3.
	 */
	private MediaType outputContentType(Map<String, Object> context, ManagedFile input) {
		if (context.get(OUTPUT_CONTENT_TYPE) instanceof String reported && StringUtils.hasText(reported))
			return MediaType.parseMediaType(reported);
		var inputContentType = MediaType.parseMediaType(input.contentType());
		return CommonMediaTypes.IMAGE.isCompatibleWith(inputContentType) ? CommonMediaTypes.JPG : CommonMediaTypes.MP3;
	}

}
