package com.joshlong.mogul.api;

import java.util.Date;
import java.util.Map;

public record SearchableResult<T extends Searchable, AGGREGATE>(long searchableId, T searchable, String title,
		String text, SearchableResultAggregate<AGGREGATE> aggregate, Map<String, Object> context, Date created) {
}
