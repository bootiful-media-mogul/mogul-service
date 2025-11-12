package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.api.search.SearchableRepository;
import com.joshlong.mogul.api.search.SearchableResult;
import com.joshlong.mogul.api.search.SearchableResultAggregate;
import com.joshlong.mogul.api.transcripts.TranscriptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.BiFunction;

@Configuration
class SegmentSearchConfiguration {

	@Bean
	SegmentSearchableRepository segmentSearchableRepository(TranscriptService transcriptService,
			PodcastService podcastService) {
		return new SegmentSearchableRepository(podcastService, transcriptService::readTranscript);
	}

}

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
	public SearchableResult<Segment, Episode> result(Segment searchable) {
		var segment = this.podcastService.getPodcastEpisodeSegmentById(searchable.searchableId());
		var episode = this.podcastService.getPodcastEpisodeById(segment.episodeId());
		var mogul = this.podcastService.getPodcastById(episode.podcastId()).mogulId();
		var episodeSearchableResult = new SearchableResultAggregate<>(episode.id(), episode);
		return new SearchableResult<>(searchable.searchableId(), searchable,
				episode.title() + ", (segment " + searchable.searchableId() + ")",
				this.transcriptLoader.apply(mogul, searchable), episodeSearchableResult);
	}

}
