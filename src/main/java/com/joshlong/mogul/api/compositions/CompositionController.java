package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

/**
 * handles looking up details associated with all compositions.
 */
@Controller
class CompositionController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final CompositionService compositionService;

	private final ManagedFileService managedFileService;

	private final MogulService mogulService;

	CompositionController(CompositionService compositionService, ManagedFileService managedFileService,
			MogulService mogulService) {
		this.compositionService = compositionService;
		this.managedFileService = managedFileService;
		this.mogulService = mogulService;
	}

	private final static String NLS = System.lineSeparator() + System.lineSeparator();

	@SchemaMapping
	String embedding(Attachment attachment) {
		var managedFile = attachment.managedFile();
		var publicUrl = this.managedFileService.getPublicUrlForManagedFile(managedFile.id());
		this.log.trace("got the public url for managed file # {} as {}", managedFile.id(), publicUrl);
		var embedding = "![%s](%s)".formatted(StringUtils.hasText(attachment.caption()) ? attachment.caption() : "",
				publicUrl);
		return NLS + embedding + NLS;
	}

	@QueryMapping
	Composition compositionById(@Argument Long compositionId) {
		return this.compositionService.getCompositionById(compositionId);
	}

	@MutationMapping
	Long deleteCompositionAttachment(@Argument Long compositionAttachmentId) {
		this.compositionService.deleteCompositionAttachment(compositionAttachmentId);
		return compositionAttachmentId;
	}

	@MutationMapping
	Attachment createCompositionAttachment(@Argument Long compositionId) {
		var mogulId = mogulService.getCurrentMogul().id();
		return this.compositionService.createCompositionAttachment(mogulId, compositionId, "");
	}

}
