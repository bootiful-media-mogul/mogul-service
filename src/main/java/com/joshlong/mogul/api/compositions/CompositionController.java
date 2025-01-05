package com.joshlong.mogul.api.compositions;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * handles looking up details associated with all compositions.
 */
@Controller
class CompositionController {

	private final CompositionService compositionService;

	CompositionController(CompositionService compositionService) {
		this.compositionService = compositionService;
	}

	@QueryMapping
	Composition compositionById(@Argument Long compositionId) {
		return this.compositionService.getCompositionById(compositionId);
	}

}
