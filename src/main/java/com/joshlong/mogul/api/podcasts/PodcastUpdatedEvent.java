package com.joshlong.mogul.api.podcasts;

/**
 * published whenever a podcast db record has been updated.
 */
public record PodcastUpdatedEvent(Podcast podcast) {
}
