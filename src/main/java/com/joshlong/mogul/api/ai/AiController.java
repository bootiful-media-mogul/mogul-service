package com.joshlong.mogul.api.ai;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;

/**
 * entry point for all requests into the AI subsystem. supports the AiChat panel on the
 * side of the screen. not sure if this should be tied to a particular mogul's openai
 * credentials or if we should just share the global one
 */
@Controller
class AiController {

	private final DefaultAiClient singularity;

	AiController(DefaultAiClient singularity) {
		Assert.notNull(singularity, "the AI client is null");
		this.singularity = singularity;
	}

	@QueryMapping
	String aiChat(@Argument String prompt) {
		return this.singularity.chat(prompt);
	}

}
