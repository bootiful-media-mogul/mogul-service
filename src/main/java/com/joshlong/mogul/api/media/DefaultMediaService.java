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
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

class DefaultMediaService implements MediaService {

	/**
	 * the name of the {@code Processor} bean over in the processors module, which is also
	 * the name a reply for us comes back under.
	 */
	static final String PROCESSOR_ID = "mediaNormalizationProcessor";

	// what it never looks at, and hands back untouched
	static final String INPUT_MANAGED_FILE_ID = "inputManagedFileId";
	static final String OUTPUT_MANAGED_FILE_ID = "outputManagedFileId";

	// what the processor reads
	private static final String INPUT_BUCKET = "inputBucket";

	private static final String INPUT_KEY = "inputKey";

	private static final String INPUT_FILENAME = "inputFilename";

	private static final String INPUT_CONTENT_TYPE = "inputContentType";

	private static final String OUTPUT_BUCKET = "outputBucket";

	private static final String OUTPUT_KEY = "outputKey";

	// what it writes back
	private static final String OUTPUT_CONTENT_TYPE = "outputContentType";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Processors processors;

	private final ManagedFileService managedFileService;

	private final ApplicationEventPublisher publisher;

	private final TransactionTemplate transactionTemplate;

	DefaultMediaService(Processors processors, ManagedFileService managedFileService,
			ApplicationEventPublisher publisher, TransactionTemplate transactionTemplate) {
		this.processors = processors;
		this.managedFileService = managedFileService;
		this.publisher = publisher;
		this.transactionTemplate = transactionTemplate;
	}

	@Override
	public void normalize(ManagedFile input, ManagedFile output, Map<String, Object> context) {
		if (!input.written()) {
			this.log.debug("the input file {} has not been written yet, so we can't normalize it", input.id());
			return;
		}
		var request = new HashMap<>(context);
		request.put(INPUT_BUCKET, input.bucket());
		request.put(INPUT_KEY, input.key());
		request.put(INPUT_FILENAME, input.filename());
		request.put(INPUT_CONTENT_TYPE, input.contentType());
		request.put(OUTPUT_BUCKET, output.bucket());
		request.put(OUTPUT_KEY, output.key());
		request.put(INPUT_MANAGED_FILE_ID, input.id());
		request.put(OUTPUT_MANAGED_FILE_ID, output.id());
		try {
			this.processors.process(PROCESSOR_ID, request);
			this.log.debug("requested the normalization of {} into {}", input.id(), output.id());
		} //
		catch (Exception e) {
			throw new RuntimeException("could not request the normalization of ManagedFile #" + input.id(), e);
		}
	}

	@ApplicationModuleListener
	void onProcessorCompleted(ProcessorCompletedEvent event) {
		if (!PROCESSOR_ID.equals(event.processorId()))
			return;
		var context = event.context();
		var inputId = (Long) context.get(INPUT_MANAGED_FILE_ID);
		var outputId = (Long) context.get(OUTPUT_MANAGED_FILE_ID);
		if (!event.success()) {
			this.log.warn("the normalization of ManagedFile #{} into #{} failed: {}", inputId, outputId,
					StringUtils.hasText(event.error()) ? event.error() : "no reason given");
			return;
		}
		var in = this.managedFileService.getManagedFileById(inputId);
		var output = this.managedFileService.getManagedFileById(outputId);
		this.managedFileService.refreshManagedFileFromStorage(output.id(), output.filename(),
				this.outputContentType(context, in));
		var out = this.managedFileService.getManagedFileById(output.id());
		this.transactionTemplate
			.executeWithoutResult(_ -> this.publisher.publishEvent(new MediaNormalizedEvent(in, out, context)));
		this.log.debug("media normalization completed for {} into {}", in.id(), out.id());
	}

	private MediaType outputContentType(Map<String, Object> context, ManagedFile input) {
		if (context.get(OUTPUT_CONTENT_TYPE) instanceof String reported && StringUtils.hasText(reported))
			return MediaType.parseMediaType(reported);
		var inputContentType = MediaType.parseMediaType(input.contentType());
		return CommonMediaTypes.IMAGE.isCompatibleWith(inputContentType) ? CommonMediaTypes.JPG : CommonMediaTypes.MP3;
	}

}
