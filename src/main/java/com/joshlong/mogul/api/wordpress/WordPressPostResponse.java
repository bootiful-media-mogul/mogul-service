package com.joshlong.mogul.api.wordpress;

public record WordPressPostResponse(int id, String link, String status, WordPressRendered title,
		WordPressRendered content) {
	public record WordPressRendered(String rendered) {
	}
}
