package com.joshlong.mogul.api.markdown;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class MarkdownController {

	private final MarkdownService service;

	MarkdownController(MarkdownService service) {
		this.service = service;
	}

	@QueryMapping
	String renderedMarkdown(@Argument String markdown) {
		return this.service.render(markdown);
	}

}
