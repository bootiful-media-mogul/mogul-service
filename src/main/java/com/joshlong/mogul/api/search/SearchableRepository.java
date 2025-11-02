package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;

public interface SearchableRepository<T extends Searchable, AGGREGATE> {

	T find(Long searchableId);

	boolean supports(Class<?> clazz);

	SearchableResult<T, AGGREGATE> result(T searchable);

	default SearchableResult<T, AGGREGATE> result(Long searchableId) {
		T searchable = find(searchableId);
		return result(searchable);
	}

}
