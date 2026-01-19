package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.EntityContext;
import com.joshlong.mogul.api.EntityContextBuilder;
import com.joshlong.mogul.api.podcasts.Podcast;
import com.joshlong.mogul.api.utils.TypeUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class PodcastEntityContextBuilder implements EntityContextBuilder<Podcast> {

	@Override
	public EntityContext buildContextFor(Long mogulId, Long podcast) {
		return new EntityContext(TypeUtils.typeName(Podcast.class), Map.of("podcastId", podcast));
	}

}
