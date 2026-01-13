package com.joshlong.mogul.api;

import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for domain services providing common resolution.
 * <p>
 * Services manage the lifecycle of domain entities (publications, transcriptions, etc.)
 * and use resolvers to load the underlying entity instances (episodes, posts, segments).
 * <p>
 * This class provides utility methods for finding the appropriate repository at runtime
 * based on the entity class type, as well as type resolution between classes and string
 * identifiers for API communication.
 *
 * @param <M> The marker interface type (e.g., Publishable, Transcribable, Composable)
 * @param <R> The repository interface type (e.g., PublishableRepository,
 * TranscribableRepository)
 */
public abstract class AbstractDomainService<M, R extends DomainResolver<M, ?>> {

	protected final Collection<R> resolvers;

	private final Map<String, Class<?>> typeMap = new ConcurrentHashMap<>();

	protected AbstractDomainService(Collection<R> resolvers) {
		this.resolvers = resolvers;
		initializeTypeMap();
	}

	private void initializeTypeMap() {
		for (var resolver : resolvers) {
			for (var cl : ReflectionUtils.genericsFor(resolver.getClass())) {
				this.typeMap.put(typeForClass(cl), cl);
			}
		}
	}

	/**
	 * Converts a class to a simple string identifier (lowercase simple name). This
	 * identifier can be sent to clients and used in APIs.
	 * @param clazz the class to convert
	 * @return the string type identifier
	 */
	protected @NonNull String typeForClass(Class<?> clazz) {
		return clazz.getSimpleName().toLowerCase();
	}

	/**
	 * Converts an entity instance to a simple string identifier for API communication.
	 * This is the primary method to use when sending type information to clients.
	 * @param entity the entity instance
	 * @return the string type identifier
	 */
	public @NonNull String typeFor(M entity) {
		return typeForClass(entity.getClass());
	}

	/**
	 * Resolves a string type identifier back to its corresponding class.
	 * @param type the string type identifier
	 * @return the resolved class
	 * @throws IllegalArgumentException if no class is found for the given type
	 */
	@SuppressWarnings("unchecked")
	protected <T extends M> Class<T> classForType(String type) {
		var match = (Class<T>) this.typeMap.getOrDefault(type, null);
		Assert.notNull(match, "couldn't find a matching class for type [" + type + "]");
		return match;
	}

	protected R findResolver(Class<? extends M> entityClass) {
		return resolvers.stream()
			.filter(repo -> repo.supports(entityClass))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No repository found for " + entityClass.getName()));
	}

	protected <T extends M> T findEntity(Class<T> entityClass, Long key) {
		var repository = (R) this.findResolver(entityClass);
		return entityClass.cast(repository.find(key));
	}

	/**
	 * Convenience method to find an entity by type name and key.
	 * @param type the string type identifier
	 * @param key the entity key
	 * @return the resolved entity
	 */
	protected <T extends M> T findEntity(String type, Long key) {
		return this.findEntity(classForType(type), key);
	}

}
