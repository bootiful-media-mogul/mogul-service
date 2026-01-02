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

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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
		return new SegmentSearchableResolver(Segment.class, podcastService, transcriptService::readTranscripts);
	}

}

class SegmentSearchableResolver extends AbstractSearchableResolver<Segment> {

	private final PodcastService podcastService;

	private final BiFunction<Long, Collection<Transcribable>, Map<Transcribable, String>> transcriptLoader;

	SegmentSearchableResolver(Class<Segment> entityClass, PodcastService podcastService,
			BiFunction<Long, Collection<Transcribable>, Map<Transcribable, String>> transcriptLoader) {
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
		var all = this.results(List.of(searchableId));
		if (all.isEmpty())
			return null;
		return all.getFirst();
	}

	@Override
	public List<SearchableResult<Segment, Episode>> results(List<Long> searchableIds) {
		var segments = podcastService.getPodcastEpisodeSegmentsByIds(searchableIds);

		// todo managedFiles r vastly too slow.
		var episodeIds = new HashSet<Long>();
		for (var segment : segments) {
			episodeIds.add(segment.episodeId());
		}
		// now we load all the episodes and correlate them back to their episode
		var episodes = podcastService.getAllPodcastEpisodesByIds(episodeIds);
		// now we need to load all the podcasts in a single batch to deduce the mogul
		var podcastIds = episodes.stream().map(Episode::podcastId).collect(Collectors.toList());
		var podcasts = podcastService.getAllPodcastsById(podcastIds);
		var segmentsToEpisodes = new HashMap<Segment, Episode>();
		for (var s : segments) {
			episodes.stream()
				.filter(e -> e.id().equals(s.episodeId()))
				.findFirst()
				.ifPresent(it -> segmentsToEpisodes.put(s, it));
		}
		var results = new ArrayList<SearchableResult<Segment, Episode>>();
		var mogulId = podcasts.iterator().next().mogulId();
		var transcribableIds = segments.stream().map(s -> (Transcribable) s).toList();
		var mapOfTranscripts = this.transcriptLoader.apply(mogulId, transcribableIds);
		var mapOfTranscribableIdsToTranscripts = new HashMap<Long, String>();
		mapOfTranscripts.forEach((key, value) -> mapOfTranscribableIdsToTranscripts.put(key.transcribableId(), value));
		for (var segment : segments) {
			var episode = segmentsToEpisodes.get(segment);
			results.add(this.buildResultFor(segment, episode, mapOfTranscribableIdsToTranscripts.get((segment.id()))));
		}
		return results;
	}

	private SearchableResult<Segment, Episode> buildResultFor(Segment segment, Episode episode, String transcript) {
		var episodeSearchableResult = new SearchableResultAggregate<>(episode.id(), episode);
		return new SearchableResult<>(segment.searchableId(), segment, episode.title(), transcript,
				episodeSearchableResult, Map.of("episodeId", episode.id()), episode.created());
	}

	@Override
	public SearchableResult<Segment, Episode> result(Segment searchable) {
		var segment = this.podcastService.getPodcastEpisodeSegmentById(searchable.searchableId());
		var episode = this.podcastService.getPodcastEpisodeById(segment.episodeId());
		var mogul = this.podcastService.getPodcastById(episode.podcastId()).mogulId();
		var episodeSearchableResult = new SearchableResultAggregate<>(episode.id(), episode);
		var transcribableStringMap = this.transcriptLoader.apply(mogul, List.of(searchable));
		var resolved = transcribableStringMap.values().iterator().next();
		return new SearchableResult<>(searchable.searchableId(), searchable, episode.title(), resolved,
				episodeSearchableResult, Map.of("episodeId", episode.id()), episode.created());
	}

}
