package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.media.MediaNormalizationMessageChannel;
import com.joshlong.mogul.api.media.Media;
import com.joshlong.mogul.api.mogul.MogulService;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class PodcastServiceConfiguration {

	@Bean
	DefaultPodcastService defaultPodcastService(CompositionService cs, Media mn, JdbcClient db, ManagedFileService mfs,
			ApplicationEventPublisher publisher, TransactionTemplate tt, CacheManager cacheManager,
			@MediaNormalizationMessageChannel MessageChannel media, MogulService mogulService) {

		var podcastsCache = cacheManager.getCache("podcasts");
		var podcastEpisodesCache = cacheManager.getCache("podcastEpisodes");
		return new DefaultPodcastService(cs, mn, db, mfs, publisher, podcastsCache, podcastEpisodesCache, mogulService,
				tt, media);
	}

}
