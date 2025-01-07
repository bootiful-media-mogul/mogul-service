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

	@SchemaMapping
	String embedding(Attachment attachment) {
		var managedFile = attachment.managedFile();
		var publicUrl = this.managedFileService.getPublicUrlForManagedFile(managedFile.id());
		this.log.trace("got the public url for managed file # {} as {}", managedFile.id(), publicUrl);
		return "![%s](%s)".formatted(StringUtils.hasText(attachment.caption()) ? attachment.caption() : "", publicUrl);
	}

	@QueryMapping
	Composition compositionById(@Argument Long compositionId) {
		return this.compositionService.getCompositionById(compositionId);
	}

	@MutationMapping
	boolean deleteCompositionAttachment(@Argument Long compositionId) {
		this.compositionService.deleteCompositionAttachment(compositionId);
		return true;
	}

	@MutationMapping
	Attachment createCompositionAttachment(@Argument Long compositionId) {
		var mogulId = mogulService.getCurrentMogul().id();
		return this.compositionService.createCompositionAttachment(mogulId, compositionId, "");
	}

}
