package com.joshlong.mogul.api;

import java.util.List;

public interface SearchableResolver<T extends Searchable, AGGREGATE> extends DomainResolver<Searchable, T> {

	List<SearchableResult<T, AGGREGATE>> results(List<Long> searchableIds);

	SearchableResult<T, AGGREGATE> result(T searchable);

	SearchableResult<T, AGGREGATE> result(Long searchableId);

}
