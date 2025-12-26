package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

//@Component
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

		private final ExecutorService fixedThreadPoolExecutor = Executors.newFixedThreadPool(this.concurrency());

		private int concurrency() {
			var concurrencyLevel = Math.max(Runtime.getRuntime().availableProcessors(), 6);
			log.info("the concurrency level is {}", concurrencyLevel);
			return concurrencyLevel;
		}

		RetroactiveIndexerRunnable(String name) {
			this.mogulName = name;
			this.mogulId = mogulService.getMogulByName(this.mogulName).id();
			log.info("{} created for mogulId # {}", getClass().getName(), this.mogulName);
		}

		@Override
		public void run() {
			log.info("{} running for mogulId # {}", getClass().getName(), mogulId);
			var allPodcastsByMogul = podcastService.getAllPodcastsByMogul(mogulId);
			log.info("there are {} podcasts for mogulId #{}", allPodcastsByMogul.size(), mogulId);
			for (var podcast : allPodcastsByMogul) {
				var episodes = podcastService.getPodcastEpisodesByPodcast(podcast.id(), false)
					.stream()
					.sorted(Comparator.comparingLong(Episode::id))
					.toList();
				log.info("episodes: found {} episodes for podcast {} for mogulId # {}", episodes.size(), podcast.id(),
						mogulId);
				var counter = new AtomicInteger(0);
				for (var episode : episodes) {
					this.fixedThreadPoolExecutor
						.submit(() -> this.ingestEpisode(counter, episodes.size(), podcast, episode, mogulId));
				}

			}
		}

		private void ingestEpisode(AtomicInteger count, int total, Podcast podcast, Episode episode, Long mogulId) {

			var current = count.incrementAndGet();

			if (!episode.complete()) {
				log.info("skipping episode # {} because it is not 'complete'", episode.id());
				return;
			}

			var segments = podcastService.getPodcastEpisodeSegmentsByEpisode(episode.id());
			log.info("there are {} episode segments for podcast episode {} for podcast {} for mogulId # {}",
					segments.size(), episode.id(), podcast.id(), mogulId);
			for (var segment : segments) {
				if (segment.audio() == null || segment.producedAudio() == null) {
					log.info("producedAudio is null, skipping.");
					continue;
				}
				log.info("indexing {}/{}: {}", current, total, segment.id());
				searchService.index(segment);
			}
		}

	}

}
