package com.joshlong.mogul.api.ai;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;

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
