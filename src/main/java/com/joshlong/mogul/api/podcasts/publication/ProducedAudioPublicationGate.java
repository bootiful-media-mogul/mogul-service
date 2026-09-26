package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.production.PodcastEpisodeRenderFinishedEvent;
import com.joshlong.mogul.api.podcasts.production.PodcastProducer;
import com.joshlong.mogul.api.publications.PublicationGate;
import com.joshlong.mogul.api.publications.PublicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * holds a publication back until the episode's audio has been rendered.
 */
class ProducedAudioPublicationGate implements PublicationGate {

	private final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * keyed by publication id, not by episode: two plugins can be publishing the same
	 * episode at once, and both of them are waiting on the one render.
	 */
	private final Map<Long, Parked> parked = new ConcurrentHashMap<>();

	private final PodcastProducer podcastProducer;

	private final PublicationService publicationService;

	ProducedAudioPublicationGate(PodcastProducer podcastProducer, PublicationService publicationService) {
		this.podcastProducer = podcastProducer;
		this.publicationService = publicationService;
	}

	@Override
	public boolean defer(PublicationService.PublicationAttempt<?> attempt, Runnable resume) {
		if (!(attempt.plugin() instanceof PodcastEpisodePublisherPlugin plugin) || !plugin.requiresProducedAudio())
			return false;
		if (!(attempt.publishContext().payload() instanceof Episode episode))
			return false;
		if (!this.stale(episode)) {
			this.log.debug("the produced audio for episode [{}] is current; publishing straight away", episode.id());
			return false;
		}
		var episodeId = episode.id();
		// if something is already rendering this episode, wait for that render rather
		// than starting a second one over the top of it: they would be writing the same
		// object, and the loser's publication would read bytes it didn't produce. the
		// look and the park have to happen together, or two publications arriving at
		// once both find nothing in flight.
		var alreadyRendering = false;
		synchronized (this.parked) {
			alreadyRendering = this.parked.values().stream().anyMatch(p -> p.episodeId().equals(episodeId));
			this.parked.put(attempt.publicationId(), new Parked(episodeId, attempt, resume));
		}
		if (alreadyRendering) {
			this.log.debug("episode [{}] is already being rendered; publication {} will wait on that render", episodeId,
					attempt.publicationId());
			return true;
		}
		try {
			this.podcastProducer.produce(episode);
		} //
		catch (Throwable throwable) {
			// nothing is going to arrive to un-park these, so they can't be left parked.
			// the attempt is ours now either way: saying "not deferred" here would go on
			// and publish an episode whose render we have just failed to even ask for.
			this.log.error("could not ask for the production of episode [{}]", episodeId, throwable);
			this.abandon(episodeId, throwable.getMessage());
		}
		return true;
	}

	@ApplicationModuleListener
	void onRenderFinished(PodcastEpisodeRenderFinishedEvent event) {
		if (event.success()) {
			for (var waiting : this.release(event.episodeId())) {
				this.log.debug("resuming publication {} now that episode [{}] has been produced",
						waiting.attempt().publicationId(), event.episodeId());
				waiting.resume().run();
			}
		} //
		else {
			// the produced audio at this key is whatever the previous render left there.
			// publishing it would be publishing the wrong episode.
			this.abandon(event.episodeId(), event.error());
		}
	}

	private void abandon(Long episodeId, String error) {
		for (var waiting : this.release(episodeId))
			this.publicationService.failPublication(waiting.attempt(),
					"the audio for episode #" + episodeId + " could not be produced: " + error);
	}

	private List<Parked> release(Long episodeId) {
		var released = new ArrayList<Parked>();
		synchronized (this.parked) {
			this.parked.values().removeIf(p -> p.episodeId().equals(episodeId) && released.add(p));
		}
		return released;
	}

	private boolean stale(Episode episode) {
		return episode.producedAudioUpdated() == null
				|| episode.producedAudioUpdated().before(episode.producedAudioAssetsUpdated());
	}

	private record Parked(Long episodeId, PublicationService.PublicationAttempt<?> attempt, Runnable resume) {
	}

}
