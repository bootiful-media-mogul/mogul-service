package com.joshlong.mogul.api;

import java.util.Collection;

/**
 * Abstract base class for domain services providing common repository resolution.
 *
 * Services manage the lifecycle of domain entities (publications, transcriptions, etc.)
 * and use repositories to load the underlying entity instances (episodes, posts,
 * segments).
 *
 * This class provides utility methods for finding the appropriate repository at runtime
 * based on the entity class type.
 *
 * @param <M> The marker interface type (e.g., Publishable, Transcribable, Composable)
 * @param <R> The repository interface type (e.g., PublishableRepository,
 * TranscribableRepository)
 */
public abstract class AbstractDomainService<M, R extends DomainRepository<M, ?>> {

	protected final Collection<R> repositories;

	protected AbstractDomainService(Collection<R> repositories) {
		this.repositories = repositories;
	}

	/**
	 * Finds the repository that handles the given entity class.
	 * @param entityClass The class to find a repository for
	 * @return The repository
	 * @throws IllegalArgumentException if no repository is found
	 */
	protected R findRepository(Class<? extends M> entityClass) {
		return repositories.stream()
			.filter(repo -> repo.supports(entityClass))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No repository found for " + entityClass.getName()));
	}

	/**
	 * Finds and loads an entity instance using the appropriate repository.
	 * @param entityClass The class of the entity to load
	 * @param key The unique identifier of the entity
	 * @return The entity instance
	 */
	protected <T extends M> T findEntity(Class<T> entityClass, Long key) {
		R repository = findRepository(entityClass);
		return entityClass.cast(repository.find(key));
	}

}
