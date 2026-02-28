package com.joshlong.mogul.api.wordpress;

public record WordPressPostResponse(int id, String status, String link, String slug, String type, RenderedField title,
		RenderedField content, RenderedField excerpt, int author, int featured_media, String date, String date_gmt) {
	public record RenderedField(String raw, String rendered) {
	}
}