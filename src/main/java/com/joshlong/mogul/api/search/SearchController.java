package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Collection;
import java.util.Map;

@Controller
class SearchController {

	private final SearchService searchService;

	SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@QueryMapping
	Collection<? extends Searchable> search(@Argument String query, @Argument Map<String, Object> metadata) {
		return this.searchService.search(query, metadata);
	}

}
