package com.joshlong.mogul.api;

import org.springframework.core.ParameterizedTypeReference;

/**
 * Generic resolver interface for the domain pattern used throughout the application.
 * <p>
 * This pattern supports cross-cutting concerns (publications, transcriptions,
 * compositions, etc.) where multiple unrelated entity types can participate in common
 * domain capabilities.
 *
 * @param <M> The marker interface type (e.g., Publishable, Transcribable, Composable)
 * @param <T> The concrete entity type that implements the marker interface
 * @see com.joshlong.mogul.api.podcasts.PodcastPublishableResolver
 * @see com.joshlong.mogul.api.blogs.PostPublishableResolver
 */
public interface DomainResolver<M, T extends M> {

	boolean supports(Class<?> clazz);

	T find(Long key);

	@SuppressWarnings("unchecked")
	default Class<T> entityType() {
		// @formatter:off
		return (Class<T>) new ParameterizedTypeReference<T>() {}
				.getType();
		// @formatter:on
	}

}
