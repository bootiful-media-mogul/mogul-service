package com.joshlong.mogul.api;

import org.springframework.core.io.Resource;

import java.io.Serializable;

/**
 * given a
 */
public interface TranscribableRepository<T extends Transcribable> {

	boolean supports(Class<?> clazz);

	T find(Long key);

	/**
	 * load the audio file as appropriate for a particular implementation of
	 * {@link Transcribable transcribable}
	 */
	Resource audio(Long key);
}
