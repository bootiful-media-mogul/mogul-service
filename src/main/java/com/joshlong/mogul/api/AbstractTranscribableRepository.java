package com.joshlong.mogul.api;

/**
 * Abstract base class for transcribable repositories providing common functionality.
 *
 * Reduces boilerplate by implementing the supports() and entityType() methods based on
 * the entity class provided in the constructor.
 *
 * Concrete implementations need to implement the find() and audio() methods.
 *
 * @param <T> The concrete entity type that implements Transcribable
 */
public abstract class AbstractTranscribableRepository<T extends Transcribable>
		extends AbstractDomainRepository<Transcribable, T> implements TranscribableRepository<T> {

	protected AbstractTranscribableRepository(Class<T> entityClass) {
		super(entityClass);
	}

}
