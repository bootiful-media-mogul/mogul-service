package com.joshlong.mogul.api;

import java.net.URI;
import java.util.Date;

public record Note(Long mogulId, Long id, String payload, Class<?> payloadClass, Date created, URI url,
		String note) implements Searchable {
	@Override
	public Long searchableId() {
		return id();
	}
}
