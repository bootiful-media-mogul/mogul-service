package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.TranscribableRepository;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
class SegmentTranscribableRepository implements TranscribableRepository<Segment> {

	private static final String PODCAST_EPISODE_CONTEXT_KEY = DefaultPodcastService.PODCAST_EPISODE_CONTEXT_KEY;

	private static final String PODCAST_EPISODE_SEGMENT_CONTEXT_KEY = DefaultPodcastService.PODCAST_EPISODE_SEGMENT_CONTEXT_KEY;

	private final PodcastService podcastService;

	private final ManagedFileService managedFileService;

	SegmentTranscribableRepository(PodcastService podcastService, ManagedFileService managedFileService) {
		this.podcastService = podcastService;
		this.managedFileService = managedFileService;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return Segment.class.isAssignableFrom(clazz);
	}

	@Override
	public Segment find(Long key) {
		return this.podcastService.getPodcastEpisodeSegmentById(key);
	}

	@Override
	public Resource audio(Long key) {
		var segment = this.find(key);
		return this.managedFileService.read(segment.producedAudio().id());
	}

	@Override
	public Map<String, Object> defaultContext(Long transcribableId) {
		var segment = this.find(transcribableId);
		return Map.of(PODCAST_EPISODE_CONTEXT_KEY, segment.episodeId(), PODCAST_EPISODE_SEGMENT_CONTEXT_KEY,
				segment.id());
	}

}
