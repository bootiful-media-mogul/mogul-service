package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.AbstractSearchableResolver;
import com.joshlong.mogul.api.SearchableResult;
import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.Segment;

import java.util.*;
import java.util.function.BiFunction;

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
		var s = this.podcastService.getPodcastEpisodeSegmentsByIds(List.of(searchableId));
		if (s.size() == 1)
			return s.iterator().next();
		return null;
	}

	@Override
	public List<SearchableResult<Segment>> results(List<Long> searchableIds) {
		if (searchableIds.isEmpty())
			return Collections.emptyList();

		var segments = this.podcastService.getPodcastEpisodeSegmentsByIds(searchableIds);
		var episodeIds = new HashSet<Long>();
		for (var segment : segments) {
			episodeIds.add(segment.episodeId());
		}
		// now we load all the episodes and correlate them back to their episode
		var episodes = this.podcastService.getAllPodcastEpisodesByIds(episodeIds);
		// now we need to load all the podcasts in a single batch to deduce the mogul
		var podcastIds = episodes.stream().map(Episode::podcastId).toList();
		var podcasts = this.podcastService.getAllPodcastsById(podcastIds);
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
			var result = this.buildResultFor(segment, episode, mapOfTranscribableIdsToTranscripts.get((segment.id())));
			results.add(result);
		}
		return results;
	}

	private SearchableResult<Segment> buildResultFor(Segment segment, Episode episode, String transcript) {
		return new SearchableResult<>(segment.searchableId(), segment, episode.title(), transcript, episode.id(),
				episode.created(), this.type);
	}

}
