package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.Transcription;

import java.util.Map;

public interface Transcriptions {

	/**
	 * returns the thing that holds the state of an ongoing transcription.
	 */
	Transcription transcription(Transcribable payload);

	/**
	 * begins the asynchronous, long-running work of transcribing a given
	 * {@link Transcribable transcribable}
	 */
	void transcribe(Long mogulId, Transcribable payload, Map<String, Object> context);

	<T extends Transcribable> TranscribableRepository<T> repositoryFor(Class<T> clazz);

}
