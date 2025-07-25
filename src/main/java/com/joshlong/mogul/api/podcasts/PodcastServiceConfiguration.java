package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.media.MediaNormalizer;
import com.joshlong.mogul.api.transcription.Transcriber;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class PodcastServiceConfiguration {

	@Bean
	DefaultPodcastService defaultPodcastService(CompositionService cs, MediaNormalizer mn, JdbcClient db,
			ManagedFileService mfs, ApplicationEventPublisher publisher, Transcriber transcriber,
			TransactionTemplate tt, CacheManager cacheManager, MogulService mogulService) {
		var podcastsCache = cacheManager.getCache("podcasts");
		var podcastEpisodesCache = cacheManager.getCache("podcastEpisodes");
		return new DefaultPodcastService(cs, mn, db, mfs, publisher, transcriber, podcastsCache, podcastEpisodesCache,
				mogulService, tt);
	}

}
