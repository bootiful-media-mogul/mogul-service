package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * handles looking up details associated with all compositions.
 */
@Controller
class CompositionController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final CompositionService compositionService;

	private final ManagedFileService managedFileService;

	CompositionController(CompositionService compositionService, ManagedFileService managedFileService) {
		this.compositionService = compositionService;
		this.managedFileService = managedFileService;
	}

	@SchemaMapping
	String embedding(Attachment attachment) {
		var managedFile = attachment.managedFile();
		var publicUrl = this.managedFileService.getPublicUrlForManagedFile(managedFile.id());
		if (this.log.isTraceEnabled())
			this.log.trace("got the public url for managed file # {} as {}", managedFile.id(), publicUrl);
		return " ![](%s) ".trim().formatted(publicUrl);
	}

	@QueryMapping
	Composition compositionById(@Argument Long compositionId) {
		return this.compositionService.getCompositionById(compositionId);
	}

}
