package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.AbstractTranscribableResolver;
import com.joshlong.mogul.utils.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
class SegmentTranscribableResolver extends AbstractTranscribableResolver<Segment> {

	private static final String PODCAST_EPISODE_CONTEXT_KEY = DefaultPodcastService.PODCAST_EPISODE_CONTEXT_KEY;

	private static final String PODCAST_EPISODE_SEGMENT_CONTEXT_KEY = DefaultPodcastService.PODCAST_EPISODE_SEGMENT_CONTEXT_KEY;

	private final PodcastService podcastService;

	SegmentTranscribableResolver(PodcastService podcastService) {
		super(Segment.class);
		this.podcastService = podcastService;
	}

	@Override
	public Segment find(Long transcribableKey) {
		return CollectionUtils
			.firstOrNull(this.podcastService.getPodcastEpisodeSegmentsByIds(List.of(transcribableKey)));
	}

	@Override
	public Audio audio(Long transcribableKey) {
		var producedAudio = this.find(transcribableKey).producedAudio();
		return new Audio(producedAudio.bucket(), producedAudio.key(), producedAudio.written());
	}

	@Override
	public Map<String, Object> defaultContext(Long transcribableKey) {
		var segment = this.find(transcribableKey);
		return Map.of(PODCAST_EPISODE_CONTEXT_KEY, segment.episodeId(), PODCAST_EPISODE_SEGMENT_CONTEXT_KEY,
				segment.id());
	}

}
