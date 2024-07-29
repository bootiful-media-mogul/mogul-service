package com.joshlong.mogul.api;

import java.util.Date;
import java.util.Map;

public record Publication(Long mogulId, Long id, String plugin, Date created, Date published,
		Map<String, String> context, String payload, Class<?> payloadClass, String url, State state) {

	public enum State {

		PUBLISHED, DRAFT, UNPUBLISHED

	}
}
