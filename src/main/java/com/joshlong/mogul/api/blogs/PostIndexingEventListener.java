package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class PostIndexingEventListener {

	private final SearchService searchService;

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	PostIndexingEventListener(SearchService searchService) {
		this.searchService = searchService;
	}

	@ApplicationModuleListener
	void postUpdatedIndexListener(PostUpdatedEvent postUpdatedEvent) {
		this.index(postUpdatedEvent.post());
	}

	@ApplicationModuleListener
	void postCreatedIndexListener(PostCreatedEvent postCreatedEvent) {
		this.index(postCreatedEvent.post());
	}

	private void index(Post post) {
		this.searchService.index(post);
		this.log.info("indexed post {}", post);
	}

}
