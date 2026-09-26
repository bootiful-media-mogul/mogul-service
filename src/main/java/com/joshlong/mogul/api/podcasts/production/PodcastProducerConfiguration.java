package com.joshlong.mogul.api.podcasts.production;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.media.MediaService;
import com.joshlong.mogul.api.podcasts.PodcastService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class PodcastProducerConfiguration {

	@Bean
	PodcastProducer podcastProducer(MediaService mediaService, ManagedFileService managedFileService,
			PodcastService podcastService, ApplicationEventPublisher publisher,
			TransactionTemplate transactionTemplate) {
		return new PodcastProducer(mediaService, managedFileService, podcastService, publisher, transactionTemplate);
	}

}
