package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableResolver;
import com.joshlong.mogul.api.Transcript;

import java.util.Collection;
import java.util.Map;

public interface TranscriptService {

	<T extends Transcribable> T transcribable(Long transcribableId, Class<T> transcribableClass);

	Transcript transcript(Long mogulId, Transcribable payload);

	Transcript transcriptById(Long id);

	void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context);

	void transcribe(Long mogulId, Long transcriptId, Map<String, Object> context);

	void transcribe(Long mogulId, Transcribable payload);

	void transcribe(Long mogulId, Long transcriptId);

	<T extends Transcribable> TranscribableResolver<T> resolverFor(Class<T> clazz);

	void writeTranscript(Transcribable transcribable, String transcript);

	void writeTranscript(Long transcriptId, String transcript);

	<T extends Transcribable> Map<Transcribable, String> readTranscripts(Long mogulId, Collection<T> toRead);

	/**
	 * the batched form of {@link #transcript(Long, Transcribable)}: the whole collection
	 * costs one query, plus one insert and one re-read if any of them didn't have a row
	 * yet. same guarantee as the single-payload version -- every payload comes back with
	 * a transcript, created if it wasn't there -- so callers still get an id to write
	 * against.
	 */
	<T extends Transcribable> Map<Transcribable, Transcript> transcripts(Long mogulId, Collection<T> payloads);

}
