package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableRepository;
import com.joshlong.mogul.api.Transcript;

import java.util.Map;

public interface TranscriptService {

	<T extends Transcribable> T transcribable(Long transcribableId, Class<T> transcribableClass);

	Transcript transcript(Long mogulId, Transcribable payload);

	Transcript transcriptById(Long id);

	void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context);

	void transcribe(Long mogulId, Long transcriptId, Map<String, Object> context);

	void transcribe(Long mogulId, Transcribable payload);

	void transcribe(Long mogulId, Long transcriptId);

	<T extends Transcribable> TranscribableRepository<T> repositoryFor(Class<T> clazz);

	void writeTranscript(Transcribable transcribable, String transcript);

	void writeTranscript(Long transcriptId, String transcript);

	<T extends Transcribable> String readTranscript(Long mogulId, T toRead);

}
