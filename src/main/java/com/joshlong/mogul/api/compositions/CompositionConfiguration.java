package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RegisterReflectionForBinding({ Composition.class, Composable.class, Attachment.class })
class CompositionConfiguration {

	@Bean
	AttachmentRowMapper attachmentRowMapper(ManagedFileService managedFileService) {
		return new AttachmentRowMapper(managedFileService::getManagedFile);
	}

	// todo fix this. the CompositionService depends on the CompositionRowMapper! so the
	// RowMapepr cant depend on the CompositionService
	// @Bean
	// CompositionRowMapper compositionRowMapper(DefaultCompositionService
	// compositionService) {
	// return new CompositionRowMapper(compositionService::getAttachmentsByComposition);
	// }

}
