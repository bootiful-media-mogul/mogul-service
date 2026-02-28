package com.joshlong.mogul.api.wordpress;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class WordPressController {

	private final WordPressClient client;

	WordPressController(WordPressClient client) {
		this.client = client;
	}

	@QueryMapping
	WordPressStatus wordPressStatus() {
		return this.client.status();
	}

}
