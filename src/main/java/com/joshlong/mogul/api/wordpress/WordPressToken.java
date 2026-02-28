package com.joshlong.mogul.api.wordpress;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Objects;

import static com.joshlong.mogul.api.wordpress.WordPressConfiguration.WORDPRESS_TOKEN_CONTEXT_KEY;

/**
 * carrier for the access token coming from WordPress' IDP
 */
abstract class WordPressToken {

	static String get() {
		var credentials = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getDetails();
		if (credentials instanceof Map<?, ?> map) {
			return (String) map.get(WORDPRESS_TOKEN_CONTEXT_KEY);
		}
		return null;
	}

	static String require() {
		return Objects.requireNonNull(get());
	}

}
