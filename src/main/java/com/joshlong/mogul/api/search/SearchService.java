package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;

import java.util.Collection;
import java.util.Map;

/**
 * implementors of the {@link Searchable searchable} type are expected to call the
 * {@link SearchService#index(Searchable)} method whenever the indexable text and metadata
 * may have reasonably changed.
 * <p>
 * TODO: should there be some sort of mapping between the {@link Searchable } and the
 * underlying implementation? After all, given the {@link Searchable#searchableId()}, how
 * do we load and resolve the source entity?
 */
public interface SearchService {

	<T extends Searchable> void index(T searchable);

	Collection<? extends Searchable> search(String query, Map<String, Object> metadata);

}
