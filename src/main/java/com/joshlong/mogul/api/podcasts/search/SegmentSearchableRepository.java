package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.api.search.SearchableRepository;
import com.joshlong.mogul.api.transcripts.TranscriptService;
import org.springframework.stereotype.Component;

/**
 * Repository for searches done against {@link Segment segments}.
 */
@Component
class SegmentSearchableRepository implements SearchableRepository<Segment, Episode> {

	private final PodcastService podcastService;

	// private final MogulService mogulService;

	private final TranscriptService transcriptService;

	SegmentSearchableRepository(PodcastService podcastService, TranscriptService transcriptService) {
		this.podcastService = podcastService;
		this.transcriptService = transcriptService;
	}

	@Override
	public Segment find(Long searchableId) {
		return this.podcastService.getPodcastEpisodeSegmentById(searchableId);
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return Segment.class.isAssignableFrom(clazz);
	}

	@Override
	public String text(Long searchableId) {
		var segment = this.find(searchableId);
		var episode = this.podcastService.getPodcastEpisodeById(segment.episodeId());
		var podcast = this.podcastService.getPodcastById(episode.podcastId());
		var mogul = podcast.mogulId();
		return this.transcriptService.readTranscript(mogul, segment);
	}

	@Override
	public String title(Long searchableId) {
		var episodeId = this.find(searchableId).episodeId();
		var episode = this.podcastService.getPodcastEpisodeById(episodeId);
		return episode.title() + ", (segment " + searchableId + ")";
	}

	@Override
	public Episode aggregate(Long searchableId) {
		// inefficient reverse traversal, but everything is cached so maybe it's not a big
		// deal.
		var segment = this.podcastService.getPodcastEpisodeSegmentById(searchableId);
		return this.podcastService.getPodcastEpisodeById(segment.episodeId());
	}

}
