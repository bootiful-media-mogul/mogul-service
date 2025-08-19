package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.api.search.SearchableRepository;
import com.joshlong.mogul.api.transcription.TranscriptionService;
import org.springframework.stereotype.Component;

@Component
class SegmentSearchableRepository implements SearchableRepository<Segment> {

	private final PodcastService podcastService;

	private final MogulService mogulService;

	private final TranscriptionService transcriptionService;

	SegmentSearchableRepository(PodcastService podcastService, MogulService mogulService,
			TranscriptionService transcriptionService) {
		this.podcastService = podcastService;
		this.mogulService = mogulService;
		this.transcriptionService = transcriptionService;
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
		var mogul = this.mogulService.getCurrentMogul();
		var segment = this.find(searchableId);
		return this.transcriptionService.readTranscript(mogul.id(), segment);
	}

}
