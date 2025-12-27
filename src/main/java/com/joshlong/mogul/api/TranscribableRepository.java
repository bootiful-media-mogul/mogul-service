package com.joshlong.mogul.api;

import org.springframework.core.io.Resource;

import java.util.Map;

/**
 * Repository interface for entities that can be transcribed.
 *
 * Provides a uniform strategy for loading {@link Transcribable} instances and accessing
 * their audio resources for transcription processing.
 *
 * Extends {@link DomainRepository} to follow the common domain pattern convention.
 *
 * @param <T> The concrete entity type that implements Transcribable
 */
public interface TranscribableRepository<T extends Transcribable> extends DomainRepository<Transcribable, T> {

	/**
	 * Loads the audio file for the transcribable entity.
	 * @param key The unique identifier of the entity
	 * @return The audio resource
	 */
	Resource audio(Long key);

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
