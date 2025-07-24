package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
class DefaultCompositionServiceConfiguration {

	@Bean
	DefaultCompositionService compositionService(AttachmentRowMapper attachmentRowMapper, JdbcClient db,
			CacheManager cacheManager, ManagedFileService managedFileService) {
		var cacheById = cacheManager.getCache("compositionsById");
		var cacheByKey = cacheManager.getCache("compositionsByKey");
		var attachments = cacheManager.getCache("compositionAttachments");
		return new DefaultCompositionService(attachmentRowMapper, db, cacheByKey, cacheById, attachments,
				managedFileService);
	}

}
