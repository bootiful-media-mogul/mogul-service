package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Transactional
class RetroactiveIndexer {

	private final Map<String, Context> contextMap = new ConcurrentHashMap<>();

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MogulService mogulService;

	private final PodcastService podcastService;

	private final SearchService searchService;

	RetroactiveIndexer(MogulService mogulService, PodcastService podcastService, SearchService searchService) {
		this.mogulService = mogulService;
		this.podcastService = podcastService;
		this.searchService = searchService;
	}

	@EventListener
	void onAuthSuccessDoIndexing(AuthenticationSuccessEvent ase) {
		var authentication = ase.getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
			this.contextMap.computeIfAbsent(authentication.getName(), s -> {
				executor.submit(new RetroactiveIndexerRunnable(jwtAuthenticationToken.getName()));
				return new Context(jwtAuthenticationToken.getName(), true, Instant.now());
			});

		}

	}

	record Context(String name, boolean transcribed, Instant instant) {
	}

	private class RetroactiveIndexerRunnable implements Runnable {

		private final String mogulName;

		private final Long mogulId;

		private final ExecutorService fixedThreadPoolExecutor = Executors.newFixedThreadPool(3);

		RetroactiveIndexerRunnable(String name) {
			this.mogulName = name;
			log.info("{} created for mogulId # {}", getClass().getName(), this.mogulName);
			this.mogulId = mogulService.getMogulByName(this.mogulName).id();
		}

		@Override
		public void run() {
			log.info("{} running for mogulId # {}", getClass().getName(), mogulId);
			var allPodcastsByMogul = podcastService.getAllPodcastsByMogul(mogulId);
			log.info("there are {} podcasts for mogulId #{}", allPodcastsByMogul.size(), mogulId);
			for (var podcast : allPodcastsByMogul) {
				var episodes = podcastService.getPodcastEpisodesByPodcast(podcast.id(), false);
				log.info("there are {} episodes for podcast {} for mogulId # {}", episodes.size(), podcast.id(),
						mogulId);
				for (var episode : episodes) {
					this.fixedThreadPoolExecutor.submit(() -> this.ingestEpisode(podcast, episode, mogulId));
				}
			}

			// looks like we made it
			this.markTranscribed();
		}

		private void ingestEpisode(Podcast podcast, Episode episode, Long mogulId) {
			if (!episode.complete()) {
				log.info("skipping episode # {} because it is not 'complete'", episode.id());
				return;
			}
			log.info("attempting to index the segments for the episode # {}", episode.id());
			var segments = podcastService.getPodcastEpisodeSegmentsByEpisode(episode.id());
			log.info("there are {} episode segments for podcast episode {} for podcast {} for mogulId # {}",
					segments.size(), episode.id(), podcast.id(), mogulId);
			for (var segment : segments) {
				if (segment.audio() == null || segment.producedAudio() == null) {
					log.info("producedAudio is null, skipping.");
					continue;
				}
				searchService.index(segment);
			}
		}

		private void markTranscribed() {
			contextMap.computeIfPresent(this.mogulName, (k, c) -> new Context(mogulName, true, c.instant()));
			log.info("finished transcription for mogul {}", this.mogulName);
		}

	}

}
