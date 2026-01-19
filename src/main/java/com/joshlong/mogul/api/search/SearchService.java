package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;
import com.joshlong.mogul.api.SearchableResult;

import java.util.Collection;
import java.util.Map;

public interface SearchService {

	void index(Searchable searchable);

	<T extends Searchable> Collection<SearchableResult<T>> search(String shouldContain, Map<String, Object> metadata);

}
