package com.joshlong.mogul.api.wordpress;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record WordPressMediaResponse(int id, String link, String status, String mime_type, String source_url, // the

		WordPressRendered title, WordPressRendered caption, @JsonProperty("alt_text") WordPressRendered altText,
		@JsonProperty("media_details") MediaDetails mediaDetails) {
	public record WordPressRendered(String rendered) {
	}

	public record MediaDetails(int width, int height, String file, Map<String, MediaSize> sizes) {
	}

	public record MediaSize(String file, int width, int height, @JsonProperty("mime_type") String mimeType,
			@JsonProperty("source_url") String sourceUrl) {
	}
}
