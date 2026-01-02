package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.AbstractSearchableResolver;
import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.api.search.SearchableResult;
import com.joshlong.mogul.api.search.SearchableResultAggregate;
import com.joshlong.mogul.api.transcripts.TranscriptService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.Map;
import java.util.function.BiFunction;

@Configuration
@ImportRuntimeHints(SegmentSearchConfiguration.Hints.class)
class SegmentSearchConfiguration {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			hints.reflection().registerType(Segment.class, MemberCategory.values());
		}

	}

	@Bean
	SegmentSearchableResolver segmentSearchableResolver(TranscriptService transcriptService,
			PodcastService podcastService) {
		return new SegmentSearchableResolver(Segment.class, podcastService, transcriptService::readTranscript);
	}

}

class SegmentSearchableResolver extends AbstractSearchableResolver<Segment> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final PodcastService podcastService;

	private final BiFunction<Long, Transcribable, String> transcriptLoader;

	SegmentSearchableResolver(Class<Segment> entityClass, PodcastService podcastService,
			BiFunction<Long, Transcribable, String> transcriptLoader) {
		super(entityClass);
		this.podcastService = podcastService;
		this.transcriptLoader = transcriptLoader;
	}

	@Override
	public Segment find(Long searchableId) {
		return this.podcastService.getPodcastEpisodeSegmentById(searchableId);
	}

	@Override
	public SearchableResult<Segment, Episode> result(Long searchableId) {
		var it = this.find(searchableId);
		return result(it);
	}

	@Override
	public SearchableResult<Segment, Episode> result(Segment searchable) {
		var segment = this.podcastService.getPodcastEpisodeSegmentById(searchable.searchableId());
		var episode = this.podcastService.getPodcastEpisodeById(segment.episodeId());
		var mogul = this.podcastService.getPodcastById(episode.podcastId()).mogulId();
		var episodeSearchableResult = new SearchableResultAggregate<>(episode.id(), episode);
		return new SearchableResult<>(searchable.searchableId(), searchable, episode.title(),
				this.transcriptLoader.apply(mogul, searchable), episodeSearchableResult,
				Map.of("episodeId", episode.id()), episode.created());
	}

}
