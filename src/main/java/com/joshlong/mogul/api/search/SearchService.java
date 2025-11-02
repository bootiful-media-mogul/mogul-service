package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;

import java.util.Collection;
import java.util.Map;

public interface SearchService {

	<T extends Searchable> void index(T searchable);

	Collection<RankedSearchResult> search(String query, Map<String, Object> metadata);

}
