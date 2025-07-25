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

	public record Outcome(int id, Date created, boolean success, URL url, String key, String serverErrorMessage) {
	}
}
