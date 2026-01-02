package com.joshlong.mogul.api;

import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.api.search.SearchableResult;

import java.util.List;

public interface SearchableResolver<T extends Searchable> extends DomainResolver<Searchable, T> {

	List<SearchableResult<Segment, Episode>> results(List<Long> searchableIds);

	SearchableResult<Segment, Episode> result(T searchable);

	SearchableResult<Segment, Episode> result(Long searchableId);

}
