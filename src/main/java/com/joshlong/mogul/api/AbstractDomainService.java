package com.joshlong.mogul.api;

import java.util.Collection;

/**
 * Abstract base class for domain services providing common resolution.
 * <p>
 * Services manage the lifecycle of domain entities (publications, transcriptions, etc.)
 * and use resolvers to load the underlying entity instances (episodes, posts, segments).
 * <p>
 * This class provides utility methods for finding the appropriate repository at runtime
 * based on the entity class type.
 *
 * @param <M> The marker interface type (e.g., Publishable, Transcribable, Composable)
 * @param <R> The repository interface type (e.g., PublishableRepository,
 * TranscribableRepository)
 */
public abstract class AbstractDomainService<M, R extends DomainResolver<M, ?>> {

	protected final Collection<R> resolvers;

	protected AbstractDomainService(Collection<R> resolvers) {
		this.resolvers = resolvers;
	}

	protected R findResolver(Class<? extends M> entityClass) {
		return resolvers.stream()
			.filter(repo -> repo.supports(entityClass))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No repository found for " + entityClass.getName()));
	}

	protected <T extends M> T findEntity(Class<T> entityClass, Long key) {
		R repository = findResolver(entityClass);
		return entityClass.cast(repository.find(key));
	}

}
