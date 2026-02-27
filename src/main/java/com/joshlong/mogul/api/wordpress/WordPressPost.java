package com.joshlong.mogul.api.wordpress;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Locale;

public record WordPressPost(String title, String content, Status status, String slug, List<Integer> categories,
		List<Integer> tags, String excerpt) {
	public enum Status {

		PUBLISH("publish"), DRAFT("draft"), PENDING("pending"), FUTURE("future");

		private final String value;

		Status(String v) {
			this.value = v;
		}

		@JsonValue
		public String getValue() {
			return value.toLowerCase(Locale.ROOT);
		}

	}
}
