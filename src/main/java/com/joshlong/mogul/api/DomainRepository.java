package com.joshlong.mogul.api;

import org.springframework.core.ParameterizedTypeReference;

/**
 * Generic repository interface for the domain pattern used throughout the application.
 *
 * This pattern supports cross-cutting concerns (publications, transcriptions,
 * compositions, etc.) where multiple unrelated entity types can participate in a common
 * domain capability.
 *
 * @param <M> The marker interface type (e.g., Publishable, Transcribable, Composable)
 * @param <T> The concrete entity type that implements the marker interface
 */
public interface DomainRepository<M, T extends M> {

	/**
	 * Checks if this repository handles the given entity class. Used for runtime
	 * repository resolution in services.
	 * @param clazz The class to check
	 * @return true if this repository can handle the given class
	 */
	boolean supports(Class<?> clazz);

	/**
	 * Finds and loads an entity instance by its key.
	 * @param key The unique identifier of the entity
	 * @return The entity instance
	 */
	T find(Long key);

	/**
	 * Returns the concrete entity class this repository handles. Useful for introspection
	 * and debugging.
	 * @return The entity class
	 */
	default Class<T> entityType() {
		// @formatter:off
		return (Class<T>) new ParameterizedTypeReference<T>() {}.getType();
		// @formatter:on
	}

}
