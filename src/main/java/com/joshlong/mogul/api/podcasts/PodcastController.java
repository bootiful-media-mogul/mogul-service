package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.compositions.Composition;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.utils.JsonUtils;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RegisterReflectionForBinding(Map.class)
class PodcastController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ApplicationEventPublisher publisher;

	private final MogulService mogulService;

	private final PodcastService podcastService;

	PodcastController(ApplicationEventPublisher publisher, MogulService mogulService, PodcastService podcastService) {
		this.publisher = publisher;
		this.mogulService = mogulService;
		this.podcastService = podcastService;
	}

	@QueryMapping
	Collection<Episode> podcastEpisodesByPodcast(@Argument Long podcastId,
			DataFetchingEnvironment dataFetchingEnvironment) {
		var graphic = isFieldRequested(dataFetchingEnvironment, "graphic");
		var segments = isFieldRequested(dataFetchingEnvironment, "segments");
		return this.podcastService.getPodcastEpisodesByPodcast(podcastId, graphic && segments);
	}

	private boolean isFieldRequested(DataFetchingEnvironment env, String fieldName) {
		return env.getSelectionSet().contains(fieldName);
	}

	// @SchemaMapping(typeName = "Query", field = "podcasts")
	// public List<Podcast> getPodcasts(DataFetchingEnvironment env) {
	// boolean wantsSegments = isFieldRequested(env, "segments");
	//
	// if (wantsSegments) {
	// // do JOIN to fetch segments eagerly
	// } else {
	// // do lightweight query
	// }
	//
	// return podcastRepository.findPodcasts(wantsSegments);
	// }

	@MutationMapping
	boolean movePodcastEpisodeSegmentDown(@Argument Long podcastEpisodeId, @Argument Long podcastEpisodeSegmentId) {
		this.podcastService.movePodcastEpisodeSegmentDown(podcastEpisodeId, podcastEpisodeSegmentId);
		return true;
	}

	@MutationMapping
	boolean movePodcastEpisodeSegmentUp(@Argument Long podcastEpisodeId, @Argument Long podcastEpisodeSegmentId) {
		this.podcastService.movePodcastEpisodeSegmentUp(podcastEpisodeId, podcastEpisodeSegmentId);
		return true;
	}

	@MutationMapping
	boolean updatePodcastEpisode(@Argument Long podcastEpisodeId, @Argument String title,
			@Argument String description) {
		this.podcastService.updatePodcastEpisodeDetails(podcastEpisodeId, title, description);
		return true;
	}

	@QueryMapping
	Episode podcastEpisodeById(@Argument Long podcastEpisodeId) {
		return this.podcastService.getPodcastEpisodeById(podcastEpisodeId);
	}

	@MutationMapping
	boolean createPodcastEpisodeSegment(@Argument Long podcastEpisodeId) {
		var mogul = this.mogulService.getCurrentMogul().id();
		this.podcastService.createPodcastEpisodeSegment(mogul, podcastEpisodeId, "", 0);
		return true;
	}

	@MutationMapping
	boolean transcribePodcastEpisodeSegment(@Argument Long podcastEpisodeSegmentId) {
		this.podcastService.transcribePodcastEpisodeSegment(podcastEpisodeSegmentId);
		return true;
	}

	@MutationMapping
	boolean setPodcastEpisodeSegmentTranscript(@Argument Long podcastEpisodeSegmentId, @Argument boolean transcribable,
			@Argument String transcript) {
		this.podcastService.setPodcastEpisodeSegmentTranscript(podcastEpisodeSegmentId, transcribable, transcript);
		return true;
	}

	@SchemaMapping
	long created(Podcast podcast) {
		return podcast.created().getTime();
	}

	@SchemaMapping
	long created(Episode episode) {
		return episode.created().getTime();
	}

	@QueryMapping
	Podcast podcastById(@Argument Long podcastId) {
		return this.podcastService.getPodcastById(podcastId);
	}

	@QueryMapping
	Collection<Podcast> podcasts() {
		var currentMogul = this.mogulService.getCurrentMogul();
		return this.podcastService.getAllPodcastsByMogul(currentMogul.id());
	}

	// @SchemaMapping
	// Collection<Episode> episodes(Podcast podcast) {
	// this.mogulService.assertAuthorizedMogul(podcast.mogulId());
	// return this.podcastService.getPodcastEpisodesByPodcast(podcast.id());
	// }

	@MutationMapping
	boolean deletePodcastEpisode(@Argument Long podcastEpisodeId) {
		this.podcastService.deletePodcastEpisode(podcastEpisodeId);
		return true;
	}

	@MutationMapping
	boolean deletePodcastEpisodeSegment(@Argument Long podcastEpisodeSegmentId) {
		this.podcastService.deletePodcastEpisodeSegment(podcastEpisodeSegmentId);
		return true;
	}

	@MutationMapping
	Episode createPodcastEpisodeDraft(@Argument Long podcastId, @Argument String title, @Argument String description) {
		var mogulId = this.mogulService.getCurrentMogul().id();
		return this.podcastService.createPodcastEpisodeDraft(mogulId, podcastId, title, description);
	}

	@MutationMapping
	boolean deletePodcast(@Argument Long podcastId) {
		var podcast = this.podcastService.getPodcastById(podcastId);
		Assert.notNull(podcast, "the podcast is null");
		var mogulId = podcast.mogulId();
		this.mogulService.assertAuthorizedMogul(mogulId);
		var podcasts = this.podcastService.getAllPodcastsByMogul(mogulId);
		Assert.state(!podcasts.isEmpty() && podcasts.size() - 1 > 0,
				"you must have at least one active, non-deleted podcast");
		this.podcastService.deletePodcast(podcast.id());
		return true;
	}

	@BatchMapping
	Map<Episode, List<Segment>> segments(List<Episode> episodes) {
		var epIds = episodes.stream().map(Episode::id).collect(Collectors.toSet());
		var allEpisodes = this.podcastService.getAllPodcastEpisodesByIds(epIds);
		var allEpisodeSegments = this.podcastService.getPodcastEpisodeSegmentsByEpisodes(epIds);
		var map = new HashMap<Episode, List<Segment>>();

		for (var ep : allEpisodes)
			map.put(ep, allEpisodeSegments.get(ep.id()));

		for (var ep : map.entrySet())
			ep.getValue().sort(Comparator.comparingInt(Segment::order));

		return map;
	}

	@SchemaMapping
	Composition titleComposition(Episode episode) {
		return this.podcastService.getPodcastEpisodeTitleComposition(episode.id());
	}

	@SchemaMapping
	Composition descriptionComposition(Episode episode) {
		return this.podcastService.getPodcastEpisodeDescriptionComposition(episode.id());
	}

	@MutationMapping
	boolean updatePodcast(@Argument Long podcastId, @Argument String title) {
		this.podcastService.updatePodcast(podcastId, title);
		return true;
	}

	@MutationMapping
	Podcast createPodcast(@Argument String title) {
		return this.podcastService.createPodcast(this.mogulService.getCurrentMogul().id(), title);
	}

	@ApplicationModuleListener
	void broadcastPodcastEpisodeCompletionEventToClients(PodcastEpisodeCompletedEvent podcastEpisodeCompletedEvent) {
		var episode = podcastEpisodeCompletedEvent.episode();
		var id = episode.id();
		try {
			var map = Map.of("episodeId", id, "complete", episode.complete());
			var json = JsonUtils.write(map);
			var notificationEvent = NotificationEvent.visibleNotificationEventFor(
					podcastEpisodeCompletedEvent.mogulId(), podcastEpisodeCompletedEvent, Long.toString(episode.id()),
					json);
			NotificationEvents.notify(notificationEvent);
		} //
		catch (Exception e) {
			this.log.warn("experienced an exception when trying to emit "
					+ "a podcast completed event for podcast episode id # {}", id);
		} //

	}

}
