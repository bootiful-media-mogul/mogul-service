package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.EntityContext;
import com.joshlong.mogul.api.EntityContextBuilder;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.utils.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class EpisodeEntityContextBuilder implements EntityContextBuilder<Episode> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final PodcastService podcastService;

	private final String resolvedType = TypeUtils.typeName(Episode.class);

	EpisodeEntityContextBuilder(PodcastService podcastService) {
		this.podcastService = podcastService;
	}

	@Override
	public EntityContext buildContextFor(Long mogulId, Long episodeId) {
		this.log.debug("resolving context for episode {}", episodeId);
		var episode = this.podcastService.getPodcastEpisodeById(episodeId);
		return new EntityContext(this.resolvedType,
				Map.of("episodeId", episode.id(), "podcastId", episode.podcastId()));
	}

}
