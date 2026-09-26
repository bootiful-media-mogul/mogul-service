package com.joshlong.mogul.api;

import java.util.Map;

/**
 *
 * Provides a uniform strategy for loading {@link Transcribable} instances and accessing
 * their audio resources for transcription processing.
 */
public interface TranscribableResolver<T extends Transcribable> extends DomainResolver<Transcribable, T> {

	/**
	 * where the audio to transcribe lives -- not the bytes. transcription happens in the
	 * {@code processors} module now, and all that has to cross the wire is a bucket and a
	 * key.
	 */
	Audio audio(Long key);

	/**
	 * one object in storage. deliberately not a {@code ManagedFile}: that lives in a
	 * module of its own, and a type in this package that referred to it would close a
	 * cycle, since that module reads its configuration from here.
	 *
	 * @param written whether anything has actually been put there yet
	 */
	record Audio(String bucket, String key, boolean written) {
	}

	/**
	 * Provides default context for the transcription event. After the transcript has been
	 * created, we'll need to publish an event that particular subsystems will need to
	 * listen to if and only if the event applies to them. We leave it up to each
	 * subsystem to furnish that configuration.
	 * @param transcribableId The unique identifier of the transcribable entity
	 * @return A map of context values
	 */
	default Map<String, Object> defaultContext(Long transcribableId) {
		return Map.of();
	}

}
