package com.joshlong.mogul.api.wordpress;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WordPressStatus(
		boolean connected,
		 long   id,
		String displayName) {
}
