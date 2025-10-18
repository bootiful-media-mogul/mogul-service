package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Collection;
import java.util.Map;

@Controller
class SearchController {

	private final SearchService searchService;

	private final Logger log = LoggerFactory.getLogger(getClass());

	SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@QueryMapping
	Collection<? extends Searchable> search(@Argument String query, @Argument Map<String, Object> metadata) {
		var search = this.searchService.search(query, metadata);
		for (var s : search) {
			this.log.debug("found {}", s);
		}
		return search;
	}

}
