package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.PublishableRepository;
import org.springframework.stereotype.Component;

@Component
class PodcastPublishableRepository implements PublishableRepository<Episode> {

	private final PodcastService podcastService;

	PodcastPublishableRepository(PodcastService podcastService) {
		this.podcastService = podcastService;
	}

	@Override
	public Episode find(Long id) {
		return podcastService.getPodcastEpisodeById(id);
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return Episode.class.isAssignableFrom(clazz);
	}

}
