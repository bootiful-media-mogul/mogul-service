package com.joshlong.mogul.api.managedfiles;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class ManagedFileServiceConfiguration {

	@Bean
	DefaultManagedFileService defaultManagedFileService(ApplicationEventPublisher publisher, CacheManager cache,
			TransactionTemplate transactionTemplate, Storage storage, JdbcClient db, ApiProperties properties,
			ApiProperties apiProperties) {
		var bucket = properties.managedFiles().s3().bucket();
		var managedFilesCache = cache.getCache("managedFiles");
		return new DefaultManagedFileService(bucket, db, storage, publisher, managedFilesCache, transactionTemplate,
				properties.aws().cloudfront().domain(), apiProperties);
	}

}
