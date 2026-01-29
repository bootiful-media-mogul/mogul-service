package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.mogul.MogulService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * handles looking up details associated with all compositions.
 */
@Controller
class CompositionController {

	private final CompositionService compositionService;

	private final MogulService mogulService;

	CompositionController(CompositionService compositionService, MogulService mogulService) {
		this.compositionService = compositionService;
		this.mogulService = mogulService;
	}

	@SchemaMapping
	String markdown(Attachment attachment) {
		return this.compositionService.createMarkdownPreviewForAttachment(attachment);
	}

	@QueryMapping
	Composition compositionById(@Argument Long compositionId) {
		return this.compositionService.getCompositionById(compositionId);
	}

	@MutationMapping
	boolean deleteCompositionAttachment(@Argument Long compositionAttachmentId) {
		this.compositionService.deleteCompositionAttachmentyId(compositionAttachmentId);
		return true;
	}

	@MutationMapping
	Attachment createCompositionAttachment(@Argument Long compositionId) {
		var mogulId = this.mogulService.getCurrentMogul().id();
		return this.compositionService.createCompositionAttachment(mogulId, compositionId, "");
	}

}
