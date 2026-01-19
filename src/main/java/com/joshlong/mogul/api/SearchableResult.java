package com.joshlong.mogul.api;

import java.util.Date;
import java.util.Map;

public record SearchableResult<T extends Searchable>(long searchableId, T searchable, String title, String text,
		long aggregateId, Date created, String type, Map<String, Object> context) {

	public SearchableResult(long searchableId, T searchable, String title, String text, long aggregateId, Date created,
			String type) {
		this(searchableId, searchable, title, text, aggregateId, created, type,
				Map.of("type", type, "id", searchableId));
	}

}