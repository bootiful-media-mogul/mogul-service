package com.joshlong.mogul.api.podcasts;

/**
 * <em>only</em> called when an episode has all the constituent parts required to publish
 * and is thus <em>complete</em>.
 */
public record PodcastEpisodeCompletionEvent(Long mogulId, Episode episode) {
}
