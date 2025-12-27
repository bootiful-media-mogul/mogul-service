package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.AbstractTranscribableResolver;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
class SegmentTranscribableResolver extends AbstractTranscribableResolver<Segment> {

	private static final String PODCAST_EPISODE_CONTEXT_KEY = DefaultPodcastService.PODCAST_EPISODE_CONTEXT_KEY;

	private static final String PODCAST_EPISODE_SEGMENT_CONTEXT_KEY = DefaultPodcastService.PODCAST_EPISODE_SEGMENT_CONTEXT_KEY;

	private final PodcastService podcastService;

	private final ManagedFileService managedFileService;

	SegmentTranscribableResolver(PodcastService podcastService, ManagedFileService managedFileService) {
		super(Segment.class);
		this.podcastService = podcastService;
		this.managedFileService = managedFileService;
	}

	@Override
	public Segment find(Long transcribableKey) {
		return this.podcastService.getPodcastEpisodeSegmentById(transcribableKey);
	}

	@Override
	public Resource audio(Long transcribableKey) {
		var segment = this.find(transcribableKey);
		return this.managedFileService.read(segment.producedAudio().id());
	}

	@Override
	public Map<String, Object> defaultContext(Long transcribableKey) {
		var segment = this.find(transcribableKey);
		return Map.of(PODCAST_EPISODE_CONTEXT_KEY, segment.episodeId(), PODCAST_EPISODE_SEGMENT_CONTEXT_KEY,
				segment.id());
	}

}
