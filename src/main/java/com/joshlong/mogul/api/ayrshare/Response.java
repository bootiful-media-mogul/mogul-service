package com.joshlong.mogul.api.ayrshare;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record Response(Status status, Map<Platform, Post> postIds, Instant scheduleDate, String id, String refId,
		String post, boolean validate) {

	public enum Status {

		ERRORS(false), SUCCESS(true), SCHEDULED(true);

		private final boolean success;

		Status(boolean success) {
			this.success = success;
		}

		public boolean success() {
			return this.success;
		}

	}

	public record Post(String type, Status status, String id, String cid, URI postUrl, Platform platform) {

	}
}
