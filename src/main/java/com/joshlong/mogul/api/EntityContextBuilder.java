package com.joshlong.mogul.api;

import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;

/**
 * Strategy interface for building navigation context for entities in search results.
 * Implementations provide the necessary context (IDs, relationships) for clients to
 * construct navigation URLs to the entity.
 *
 * @param <T> the entity type for which context is built
 */
public interface EntityContextBuilder<T> {

	@SuppressWarnings("unchecked")
	static <Y> EntityContextBuilder<Y> contextBuilderFor(ApplicationContext applicationContext, Class<Y> clazz) {
		var resolvableType = ResolvableType.forClass(EntityContextBuilder.class, clazz);
		var beanNamesForType = applicationContext.getBeanNamesForType(resolvableType, true, false);
		if (beanNamesForType.length == 0) {
			throw new IllegalStateException("couldn't find a bean supporting this parameter");
		}
		return (EntityContextBuilder<Y>) applicationContext.getBean(beanNamesForType[0], EntityContextBuilder.class);
	}

	/**
	 * Builds a context map containing the keys and values needed to navigate to this
	 * entity. The client uses this context to construct the appropriate URL.
	 * @return a map of context keys and values (e.g., "podcastId", "episodeId")
	 */
	EntityContext buildContextFor(Long mogulId, Long entityId);

}
