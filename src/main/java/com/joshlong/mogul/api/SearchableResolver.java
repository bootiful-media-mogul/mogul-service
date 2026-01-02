package com.joshlong.mogul.api;

import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.api.search.SearchableResult;

public interface SearchableResolver<T extends Searchable> extends DomainResolver<Searchable, T> {


    SearchableResult<Segment, Episode> result(T searchable);
    SearchableResult<Segment, Episode> result(Long searchableId);
}
