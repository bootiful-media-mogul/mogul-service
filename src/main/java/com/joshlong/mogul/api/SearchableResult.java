package com.joshlong.mogul.api;

import java.util.Date;
import java.util.Map;

public record SearchableResult<T extends Searchable>(long searchableId, T searchable, String title, String text,
		long aggregateId, Map<String, Object> context, Date created, float rank, String type) {
}
