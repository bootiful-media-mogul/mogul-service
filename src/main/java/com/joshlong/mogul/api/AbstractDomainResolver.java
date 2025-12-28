package com.joshlong.mogul.api;

/**
 * Abstract base class for domain resolvers providing common functionality.
 * <p>
 * Implements the supports() and entityType() methods based on the entity class provided
 * in the constructor, reducing boilerplate in concrete implementations.
 *
 * @param <M> The marker interface type (e.g., Publishable, Transcribable, Composable)
 * @param <T> The concrete entity type that implements the marker interface
 */
public abstract class AbstractDomainResolver<M, T extends M> implements DomainResolver<M, T> {

	private final Class<T> entityClass;

	protected AbstractDomainResolver(Class<T> entityClass) {
		this.entityClass = entityClass;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return entityClass.equals(clazz);
	}

	@Override
	public Class<T> entityType() {
		return entityClass;
	}

}
