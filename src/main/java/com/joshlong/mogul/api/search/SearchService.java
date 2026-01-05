package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;
import com.joshlong.mogul.api.SearchableResult;

import java.util.Collection;
import java.util.Map;

public interface SearchService {

	<T extends Searchable> void index(T searchable);

	Collection<SearchableResult<? extends Searchable>> search(String query, Map<String, Object> metadata);

}
