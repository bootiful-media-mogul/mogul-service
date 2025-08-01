package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableRepository;
import com.joshlong.mogul.api.Transcription;

import java.util.Map;

public interface TranscriptionService {

	Transcription transcription(Long mogulId, Transcribable payload);

	Transcription transcriptionById(Long id);

	void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context);

	void transcribe(Long mogulId, Long transcriptionId, Map<String, Object> context);

	void transcribe(Long mogulId, Transcribable payload);

	void transcribe(Long mogulId, Long transcriptionId);

	<T extends Transcribable> TranscribableRepository<T> repositoryFor(Class<T> clazz);

	void writeTranscript(Transcribable transcribable, String transcript);

	void writeTranscript(Long transcriptionId, String transcript);

}
