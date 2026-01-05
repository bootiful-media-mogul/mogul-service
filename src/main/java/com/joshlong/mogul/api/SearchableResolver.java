package com.joshlong.mogul.api;

import java.util.List;

public interface SearchableResolver<T extends Searchable> extends DomainResolver<Searchable, T> {

	List<SearchableResult<T>> results(List<Long> searchableIds);

}
