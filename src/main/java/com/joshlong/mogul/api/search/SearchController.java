package com.joshlong.mogul.api.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Controller
class SearchController {

	private final SearchService searchService;

	private final Logger log = LoggerFactory.getLogger(getClass());

	SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@QueryMapping
	Collection<SearchResult> search(@Argument String query, @Argument Map<String, Object> metadata) {
		var search = this.searchService.search(query, metadata);
		for (var s : search) {
			this.log.debug("found {}", s);
		}
		// todo figure out how to map the Searchables into SearchResult objects
		// todo can the SearchResults be put in the root package? Might other parts of the
		// system want to talk to the Search API
		// in terms of the SearchResult object?
		return search;
	}

}
