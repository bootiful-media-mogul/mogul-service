package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.AbstractNotableResolver;
import org.springframework.stereotype.Component;

@Component
class PodcastNotableResolver extends AbstractNotableResolver<Podcast> {

	PodcastNotableResolver() {
		super(Podcast.class);
	}

	@Override
	public Podcast find(Long key) {
		return null;
	}

}
