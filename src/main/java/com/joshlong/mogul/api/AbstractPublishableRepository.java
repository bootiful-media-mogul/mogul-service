package com.joshlong.mogul.api;

/**
 * Abstract base class for publishable repositories providing common functionality.
 *
 * Reduces boilerplate by implementing the supports() and entityType() methods based on
 * the entity class provided in the constructor.
 *
 * Concrete implementations only need to implement the find() method.
 *
 * @param <T> The concrete entity type that implements Publishable
 */
public abstract class AbstractPublishableRepository<T extends Publishable>
		extends AbstractDomainRepository<Publishable, T> implements PublishableRepository<T> {

	protected AbstractPublishableRepository(Class<T> entityClass) {
		super(entityClass);
	}

}
