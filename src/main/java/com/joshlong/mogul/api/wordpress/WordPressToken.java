package com.joshlong.mogul.api.wordpress;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Objects;

import static com.joshlong.mogul.api.wordpress.WordPressConfiguration.WORDPRESS_TOKEN_CONTEXT_KEY;

abstract class WordPressToken {

	static String get() {
		var credentials = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getDetails();
		if (credentials instanceof Map<?, ?> map) {
			return (String) map.get(WORDPRESS_TOKEN_CONTEXT_KEY);
		}
		throw new IllegalStateException("No WordPress token found in authentication details");
	}

}
