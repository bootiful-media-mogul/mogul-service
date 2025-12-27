package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.AbstractPublishableRepository;
import org.springframework.stereotype.Component;

@Component
class PodcastPublishableRepository extends AbstractPublishableRepository<Episode> {

	private final PodcastService podcastService;

	PodcastPublishableRepository(PodcastService podcastService) {
		super(Episode.class);
		this.podcastService = podcastService;
	}

	@Override
	public Episode find(Long id) {
		return podcastService.getPodcastEpisodeById(id);
	}

}
