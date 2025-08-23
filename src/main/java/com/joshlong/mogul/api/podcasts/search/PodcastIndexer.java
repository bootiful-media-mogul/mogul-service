package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.podcasts.PodcastEpisodeCompletedEvent;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.search.SearchService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class PodcastIndexer {

	private final SearchService searchService;

	private final PodcastService podcastService;

	PodcastIndexer(SearchService searchService, PodcastService podcastService) {
		this.searchService = searchService;
		this.podcastService = podcastService;
	}

	@ApplicationModuleListener
	void podcastCompleted(PodcastEpisodeCompletedEvent event) {

		if (!event.episode().complete())
			return;

		var episode = event.episode();

		for (var segment : this.podcastService.getPodcastEpisodeSegmentsByEpisode(episode.id())) {
			this.searchService.index(segment);
		}
	}

}
