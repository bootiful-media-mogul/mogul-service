package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.api.search.SearchableRepository;
import com.joshlong.mogul.api.transcripts.TranscriptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.function.BiFunction;

@Configuration
class SegmentSearchConfiguration {

	// todo i dont like that i need to use @Lazy. circular dependency somewhere
	@Bean
	SegmentSearchableRepository segmentSearchableRepository(/* @Lazy */ TranscriptService transcriptService,
			PodcastService podcastService) {
		return new SegmentSearchableRepository(podcastService, transcriptService::readTranscript);
	}

}

/**
 * Repository for searches done against {@link Segment segments}.
 */
class SegmentSearchableRepository implements SearchableRepository<Segment, Episode> {

	private final PodcastService podcastService;

	private final BiFunction<Long, Transcribable, String> transcriptLoader;

	SegmentSearchableRepository(PodcastService podcastService,
			BiFunction<Long, Transcribable, String> transcriptLoader) {
		this.podcastService = podcastService;
		this.transcriptLoader = transcriptLoader;
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
		return this.transcriptLoader.apply(mogul, segment);
	}

	@Override
	public String title(Long searchableId) {
		var episodeId = this.find(searchableId).episodeId();
		var episode = this.podcastService.getPodcastEpisodeById(episodeId);
		return episode.title() + ", (segment " + searchableId + ")";
	}

	@Override
	public Episode aggregate(Long searchableId) {
		var segment = this.podcastService.getPodcastEpisodeSegmentById(searchableId);
		return this.podcastService.getPodcastEpisodeById(segment.episodeId());
	}

}
