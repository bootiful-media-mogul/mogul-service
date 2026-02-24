package com.joshlong.mogul.api.wordpress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import static com.joshlong.mogul.api.wordpress.WordPressConfiguration.WORDPRESS_TOKEN_CONTEXT_KEY;

@Controller
class WordPressController {

	private final WordPressClient client;

	private final Logger log = LoggerFactory.getLogger(getClass());

	WordPressController(WordPressClient client) {
		this.client = client;
	}

	@QueryMapping
	WordPressStatus wordPressStatus(
			@ContextValue(value = WORDPRESS_TOKEN_CONTEXT_KEY, required = false) String wpToken) {
		return this.client.status();
	}

}
