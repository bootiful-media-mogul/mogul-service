package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.AbstractPublishableResolver;
import org.springframework.stereotype.Component;

@Component
class PodcastPublishableResolver extends AbstractPublishableResolver<Episode> {

	private final PodcastService podcastService;

	PodcastPublishableResolver(PodcastService podcastService) {
		super(Episode.class);
		this.podcastService = podcastService;
	}

	@Override
	public Episode find(Long id) {
		return podcastService.getPodcastEpisodeById(id);
	}

}
