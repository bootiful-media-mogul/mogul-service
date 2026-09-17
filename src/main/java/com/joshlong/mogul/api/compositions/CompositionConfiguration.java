package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.compositions.attachments.previews.MarkdownPreview;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@RegisterReflectionForBinding({ Composition.class, Composable.class, Attachment.class })
class CompositionConfiguration {

	@Bean
	DefaultCompositionService compositionService(MarkdownPreview[] previews, JdbcClient db, CacheManager cacheManager,
			ManagedFileService managedFileService) {
		var cacheById = cacheManager.getCache("compositionsById");
		var cacheByKey = cacheManager.getCache("compositionsByKey");
		var attachments = cacheManager.getCache("compositionAttachments");
		return new DefaultCompositionService(db, cacheByKey, cacheById, attachments, managedFileService, previews);
	}

}
