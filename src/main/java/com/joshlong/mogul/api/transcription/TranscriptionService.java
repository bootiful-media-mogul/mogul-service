package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.TranscribableRepository;
import com.joshlong.mogul.api.Transcription;

import java.util.Map;

public interface TranscriptionService {

	/**
	 * returns the thing that holds the state of an ongoing transcription.
	 */
	Transcription transcription(Long mogulId, Transcribable payload);

	Transcription transcriptionById(Long id);

	/**
	 * begins the asynchronous, long-running work of transcribing a given
	 * {@link Transcribable transcribable}
	 */
	void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context);

	void transcribe(Long mogulId, Long transcriptionId, Map<String, Object> context);

	/**
	 * these variants will obtain the default context from the repository.
	 */
	void transcribe(Long mogulId, Transcribable payload);

	/**
	 * these variants will obtain the default context from the repository.
	 */
	void transcribe(Long mogulId, Long transcriptionId);

	<T extends Transcribable> TranscribableRepository<T> repositoryFor(Class<T> clazz);

	void writeTranscript(Transcribable transcribable, String transcript);

	void writeTranscript(Long transcriptionId, String transcript);

}
