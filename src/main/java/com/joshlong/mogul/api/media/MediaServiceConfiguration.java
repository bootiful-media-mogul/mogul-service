package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.processors.Processors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class MediaServiceConfiguration {

	@Bean
	DefaultMediaService mediaService(Processors processors, ManagedFileService managedFileService,
			ApplicationEventPublisher publisher, TransactionTemplate transactionTemplate) {
		return new DefaultMediaService(processors, managedFileService, publisher, transactionTemplate);
	}

}
