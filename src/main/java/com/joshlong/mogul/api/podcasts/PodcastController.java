package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.Transcript;
import com.joshlong.mogul.api.compositions.Composition;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.transcripts.TranscriptService;
import com.joshlong.mogul.api.utils.DateUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RegisterReflectionForBinding(Map.class)
class PodcastController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MogulService mogulService;

	private final PodcastService podcastService;

	private final TranscriptService transcriptService;

	PodcastController(MogulService mogulService, PodcastService podcastService, TranscriptService transcriptService) {
		this.mogulService = mogulService;
		this.podcastService = podcastService;
		this.transcriptService = transcriptService;
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

	@SchemaMapping
	Collection<Episode> episodes(Podcast podcast) {
		return podcastService.getPodcastEpisodesByPodcast(podcast.id(), false);
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
	Long createPodcastEpisodeSegment(@Argument Long podcastEpisodeId) {
		var mogul = this.mogulService.getCurrentMogul().id();
		var segment = this.podcastService.createPodcastEpisodeSegment(mogul, podcastEpisodeId, "", 0);
		return segment.id();
	}

	@SchemaMapping
	OffsetDateTime created(Podcast podcast) {
		return DateUtils.forDate(podcast.created());
	}

	@SchemaMapping
	OffsetDateTime created(Episode episode) {
		return DateUtils.forDate(episode.created());
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

		for (var ep : map.entrySet()) {
			Assert.state(ep.getValue() != null, "no segments found for episode id #" + ep.getKey().id() + ","
					+ "and there must be at least one segment for each episode");
			ep.getValue().sort(Comparator.comparingInt(Segment::order));
		}
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
	void broadcastPodcastUpdatedEventToClients(PodcastUpdatedEvent podcastUpdatedEvent) {
		var ne = NotificationEvent.visibleNotificationEventFor(podcastUpdatedEvent.podcast().mogulId(),
				podcastUpdatedEvent, Long.toString(podcastUpdatedEvent.podcast().id()),
				podcastUpdatedEvent.podcast().title());
		NotificationEvents.notify(ne);
	}

	@ApplicationModuleListener
	void broadcastPodcastEpisodeCompletionEventToClients(PodcastEpisodeCompletedEvent podcastEpisodeCompletedEvent) {
		var episode = podcastEpisodeCompletedEvent.episode();
		var id = episode.id();
		try {
			var map = Map.of("episodeId", id, "complete", episode.complete());
			var json = JsonUtils.write(map);
			var notificationEvent = NotificationEvent.systemNotificationEventFor(podcastEpisodeCompletedEvent.mogulId(),
					podcastEpisodeCompletedEvent, Long.toString(episode.id()), json);
			NotificationEvents.notify(notificationEvent);
		} //
		catch (Exception e) {
			this.log.warn("experienced an exception when trying to emit "
					+ "a podcast completed event for podcast episode id # {}", id);
		} //
	}

	@SchemaMapping
	Transcript transcript(Segment segment) {
		var mogul = this.mogulService.getCurrentMogul();
		return this.transcriptService.transcript(mogul.id(), segment);
	}

}
