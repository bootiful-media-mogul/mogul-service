package com.joshlong.mogul.api.wordpress;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WordPressStatus(@JsonProperty("avatar_URL") String avatarUrl, boolean connected, String email,
		String displayName) {
}
