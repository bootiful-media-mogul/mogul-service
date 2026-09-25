package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.processors.Processors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * handles normalizing media like audio and images. the work itself -- {@code ffmpeg},
 * {@code magick}, and the CPU they monopolize -- lives in the {@code processors} module
 * now; what is left here is the request, the bookkeeping that survives the wait, and the
 * {@link MediaNormalizedEvent mediaNormalizedEvent} published on success.
 */
@Configuration
class MediaServiceConfiguration {

	@Bean
	DefaultMediaService mediaService(Processors processors, ManagedFileService managedFileService,
			ApplicationEventPublisher publisher, JdbcClient db, JsonMapper jsonMapper,
			TransactionTemplate transactionTemplate, PlatformTransactionManager transactionManager) {
		return new DefaultMediaService(processors, managedFileService, publisher, db, jsonMapper, transactionTemplate,
				newTransactionTemplate(transactionManager));
	}

	/**
	 * a record of the request has to outlive the transaction that made it, and be visible
	 * to another thread before the reply it is the key to can arrive.
	 */
	private static TransactionTemplate newTransactionTemplate(PlatformTransactionManager transactionManager) {
		var template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return template;
	}

}
