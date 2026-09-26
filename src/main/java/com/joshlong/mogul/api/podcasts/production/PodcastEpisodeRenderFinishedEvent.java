package com.joshlong.mogul.api.podcasts.production;

public record PodcastEpisodeRenderFinishedEvent(long episodeId, boolean success, String error) {
}
