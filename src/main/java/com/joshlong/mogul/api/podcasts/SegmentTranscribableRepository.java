package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.transcription.TranscribableRepository;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
class SegmentTranscribableRepository implements TranscribableRepository<Segment> {

	private final PodcastService podcastService;

	SegmentTranscribableRepository(PodcastService podcastService) {
		this.podcastService = podcastService;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return Segment.class.isAssignableFrom(clazz);
	}

	@Override
	public Segment find(Serializable serializable) {
		return this.podcastService.getPodcastEpisodeSegmentById((Long) serializable);
	}

}
