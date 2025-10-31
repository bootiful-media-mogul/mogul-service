package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;

import java.util.*;

/**
 * the main interface by which to consume search functionality across the rest of the
 * system.
 */
public interface SearchService {

	<T extends Searchable> void index(T searchable);

	Collection<SearchResult> search(String query, Map<String, Object> metadata);
	// Collection<? extends Searchable> search(String query, Map<String, Object>
	// metadata);

}
