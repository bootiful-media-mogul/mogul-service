package com.joshlong.mogul.api.podcasts.production;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.media.AudioProducedEvent;
import com.joshlong.mogul.api.media.MediaService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.Segment;
import com.joshlong.mogul.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * turns an episode's segments into the one audio file that gets published.
 */
public class PodcastProducer {

	static final String PODCAST_EPISODE_ID = "podcastEpisodeId";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MediaService mediaService;

	private final ManagedFileService managedFileService;

	private final PodcastService podcastService;

	private final ApplicationEventPublisher publisher;

	private final TransactionTemplate transactionTemplate;

	PodcastProducer(MediaService mediaService, ManagedFileService managedFileService, PodcastService podcastService,
			ApplicationEventPublisher publisher, TransactionTemplate transactionTemplate) {
		this.mediaService = mediaService;
		this.managedFileService = managedFileService;
		this.podcastService = podcastService;
		this.publisher = publisher;
		this.transactionTemplate = transactionTemplate;
		Assert.notNull(this.mediaService, "the MediaService reference is required");
		Assert.notNull(this.managedFileService, "the ManagedFileService reference is required");
		Assert.notNull(this.podcastService, "the PodcastService reference is required");
	}

	public void produce(Episode episode) {
		var segments = this.podcastService.getPodcastEpisodeSegmentsByEpisode(episode.id());
		Assert.state(!segments.isEmpty(), () -> "episode #" + episode.id() + " has no segments to produce");
		var inputs = segments.stream().map(Segment::producedAudio).toList();
		var mogulId = episode.producedAudio().mogulId();
		this.log.debug("requesting the production of episode [{}] from {} segment(s)", episode.id(), inputs.size());
		this.mediaService.produce(inputs, episode.producedAudio(), Map.of(PODCAST_EPISODE_ID, (Object) episode.id()));
		NotificationEvents.notifyAsync(NotificationEvent.visibleNotificationEventFor(mogulId,
				new PodcastEpisodeRenderStartedEvent(episode.id()), Long.toString(episode.id()), null));
	}

	@ApplicationModuleListener
	void onAudioProduced(AudioProducedEvent event) {
		if (!(event.context().get(PODCAST_EPISODE_ID) instanceof Long episodeId))
			return; // somebody else's render.
		var out = event.out();
		if (event.success()) {
			// the bytes are in S3 and the ManagedFile row already knows it; all that is
			// left is to let the episode say when, and to let the world at the file.
			this.managedFileService.setManagedFileVisibility(out.id(), true);
			this.podcastService.writePodcastEpisodeProducedAudio(episodeId, out.id());
			this.log.debug("produced the audio for episode [{}] into ManagedFile [{}]", episodeId, out.id());
		} //
		else {
			this.log.warn("could not produce the audio for episode [{}]: {}", episodeId, event.error());
		}
		var finished = new PodcastEpisodeRenderFinishedEvent(episodeId, event.success(), event.error());
		this.transactionTemplate.executeWithoutResult(_ -> this.publisher.publishEvent(finished));
		// there is a publication sitting in draft waiting on this, and what the client
		// shows next depends on which way it went, so say which.
		var json = JsonUtils.write(Map.of("episodeId", episodeId, "success", event.success()));
		NotificationEvents.notifyAsync(
				NotificationEvent.visibleNotificationEventFor(out.mogulId(), finished, Long.toString(episodeId), json));
	}

}
