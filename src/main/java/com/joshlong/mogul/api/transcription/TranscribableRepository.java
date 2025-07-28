package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;

import java.io.Serializable;

/**
 * given a
 */
public interface TranscribableRepository<T extends Transcribable> {

	boolean supports(Class<?> clazz);

	T find(Serializable serializable);

}
