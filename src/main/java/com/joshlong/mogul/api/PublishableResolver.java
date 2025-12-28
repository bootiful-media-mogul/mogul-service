package com.joshlong.mogul.api;

/**
 * Repository interface for entities that can be published.
 * <p>
 * Provides a uniform strategy for loading {@link Publishable} instances across different
 * subsystems. The logic differs based on the subsystem; e.g.: the
 * {@link com.joshlong.mogul.api.blogs.BlogService blogService} can resolve
 * {@link com.joshlong.mogul.api.blogs.Post posts}, and the
 * {@link com.joshlong.mogul.api.podcasts.PodcastService podcastService} can resolve
 * {@link com.joshlong.mogul.api.podcasts.Episode episodes}.
 * <p>
 * Extends {@link DomainResolver} to follow the common domain pattern convention.
 *
 * @param <T> The concrete entity type that implements Publishable
 */
public interface PublishableResolver<T extends Publishable> extends DomainResolver<Publishable, T> {

}
