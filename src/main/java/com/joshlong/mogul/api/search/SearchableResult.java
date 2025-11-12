package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;

public record SearchableResult<T extends Searchable, AGGREGATE>(long searchableId, T searchable, String title,
		String text, SearchableResultAggregate<AGGREGATE> aggregate) {
}
