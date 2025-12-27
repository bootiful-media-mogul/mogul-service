package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.AbstractNotableRepository;
import org.springframework.stereotype.Component;

@Component
class PodcastNotableRepository extends AbstractNotableRepository<Podcast> {

	PodcastNotableRepository() {
		super(Podcast.class);
	}

	@Override
	public Podcast find(Long key) {
		return null;
	}

}
