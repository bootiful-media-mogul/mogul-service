package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.AbstractSearchableResolver;
import com.joshlong.mogul.api.SearchableResult;
import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.Podcast;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.api.transcripts.TranscriptService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.*;
import java.util.function.BiFunction;

@Configuration
@ImportRuntimeHints(SegmentSearchConfiguration.Hints.class)
class SegmentSearchConfiguration {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
			for (var c : Set.of(Segment.class, Podcast.class, Episode.class))
				hints.reflection().registerType(c, MemberCategory.values());
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
	public List<SearchableResult<Segment>> results(List<Long> searchableIds) {
		if (searchableIds.isEmpty())
			return Collections.emptyList();

		var segments = podcastService.getPodcastEpisodeSegmentsByIds(searchableIds);
		var episodeIds = new HashSet<Long>();
		for (var segment : segments) {
			episodeIds.add(segment.episodeId());
		}
		// now we load all the episodes and correlate them back to their episode
		var episodes = podcastService.getAllPodcastEpisodesByIds(episodeIds);
		// now we need to load all the podcasts in a single batch to deduce the mogul
		var podcastIds = episodes.stream().map(Episode::podcastId).toList();
		var podcasts = podcastService.getAllPodcastsById(podcastIds);
		var segmentsToEpisodes = new HashMap<Segment, Episode>();
		for (var s : segments) {
			episodes.stream()
				.filter(e -> e.id().equals(s.episodeId()))
				.findFirst()
				.ifPresent(it -> segmentsToEpisodes.put(s, it));
		}
		var results = new ArrayList<SearchableResult<Segment>>();
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

	private SearchableResult<Segment> buildResultFor(Segment segment, Episode episode, String transcript) {
		var context = Map.<String, Object>of("episodeId", episode.id(), "segmentId", segment.id(), "podcastId",
				episode.podcastId());
		return new SearchableResult<>(segment.searchableId(), segment, episode.title(), transcript, episode.id(),
				context, episode.created(), 0, type(Segment.class));
	}

	private static String type(@NonNull Class<?> clzz) {
		return (clzz.getSimpleName().charAt(0) + "").toLowerCase() + clzz.getSimpleName().substring(1);
	}

}
