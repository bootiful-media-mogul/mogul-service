package com.joshlong.mogul.api;

import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;

public record Publication(Long mogulId, Long id, String plugin, Date created, Date published,
		Map<String, String> context, String payload, Class<?> payloadClass, State state, List<Outcome> outcomes) {

	public enum State {

		PUBLISHED, DRAFT, UNPUBLISHED

	}

	/**
	 * the record of one thing a plugin did: a link, and -- when the thing published is
	 * itself worth a glance, like the text of a tweet -- a short one-line {@code preview}
	 * of it. the preview is already abbreviated by the time it lands here; see
	 * {@link PublisherPlugin.PublishContext#PREVIEW_LENGTH}. most outcomes have nothing
	 * useful to preview and leave it {@code null}.
	 */
	public record Outcome(int id, Date created, boolean success, URL url, String key, String serverErrorMessage,
			String preview) {
	}
}
