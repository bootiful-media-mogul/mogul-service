package com.joshlong.mogul.api;

import org.springframework.core.io.Resource;

import java.util.Map;

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

	/**
	 * the idea is that after the transcription is done, we'll need to publish an event
	 * that particular subsystems will need to listen to if and only if the event applies
	 * to them. we leave it up to each subsystem to furnish that configuration.
	 */
	default Map<String, Object> defaultContext(Long transcribableId) {
		return Map.of();
	}

}
