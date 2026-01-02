package com.joshlong.mogul.api;

/**
 * Abstract base class for publishable resolvers providing common functionality.
 * <p>
 * Reduces boilerplate by implementing the supports() and entityType() methods based on
 * the entity class provided in the constructor.
 * <p>
 * Concrete implementations only need to implement the find() method.
 *
 * @param <T> The concrete entity type that implements Publishable
 */
public abstract class AbstractPublishableResolver<T extends Publishable> extends AbstractDomainResolver<Publishable, T>
		implements PublishableResolver<T> {

	protected AbstractPublishableResolver(Class<T> entityClass) {
		super(entityClass);
	}

}

