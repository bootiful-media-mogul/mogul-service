package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.AbstractNotableResolver;
import org.springframework.stereotype.Component;

@Component
class EpisodeNotableResolver extends AbstractNotableResolver<Episode> {

	private final PodcastService podcastService;

	EpisodeNotableResolver(PodcastService podcastService) {
		super(Episode.class);
		this.podcastService = podcastService;
	}

	@Override
	public Episode find(Long key) {
		return this.podcastService.getPodcastEpisodeById(key);
	}

}
