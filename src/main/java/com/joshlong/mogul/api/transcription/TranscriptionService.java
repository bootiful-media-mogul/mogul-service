package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.Transcription;

public interface TranscriptionService {

	/**
	 * returns the thing that holds the state of an ongoing transcription.
	 */
	Transcription transcription(Transcribable payload);

	/**
	 * begins the asynchronous, long-running work of transcribing a given
	 * {@link Transcribable transcribable}
	 */
	void transcribe(Transcribable payload);

	<T extends Transcribable> TranscribableRepository<T> repositoryFor(Class<T> clazz);

}
