package com.joshlong.mogul.api.podcasts.search;

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

import java.util.Set;

@Configuration
@ImportRuntimeHints(SegmentSearchConfiguration.Hints.class)
class SegmentSearchConfiguration {

	@Bean
	SegmentSearchableResolver segmentSearchableResolver(TranscriptService transcriptService,
			PodcastService podcastService, SegmentEntityContextBuilder contextBuilder) {
		return new SegmentSearchableResolver(Segment.class, podcastService, transcriptService::readTranscripts);
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
			for (var c : Set.of(Segment.class, Podcast.class, Episode.class))
				hints.reflection().registerType(c, MemberCategory.values());
		}

	}

}
