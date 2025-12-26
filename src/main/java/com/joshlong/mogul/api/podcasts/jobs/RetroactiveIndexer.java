package com.joshlong.mogul.api.podcasts.jobs;

import com.joshlong.mogul.api.jobs.MogulJob;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.Podcast;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
class RetroactiveIndexer implements MogulJob {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final int concurrency = this.concurrency();

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final PodcastService podcastService;

	private final SearchService searchService;

	RetroactiveIndexer(PodcastService podcastService, SearchService searchService) {
		this.podcastService = podcastService;
		this.searchService = searchService;
	}

	@Override
	public Result run(Map<String, Object> context) throws Exception {
		var mogulId = (Long) context.get(MogulJob.MOGUL_ID_KEY);
		this.run(mogulId);
		return Result.ok(context);
	}

	private int concurrency() {
		return Math.max(Runtime.getRuntime().availableProcessors(), 6);
	}

	private void run(Long mogulId) {
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

			var mapDivided = this.divide(episodes);
			var workQueuesCompletableFutures = new ArrayList<CompletableFuture<Void>>();
			for (var entry : mapDivided.entrySet()) {
				var cf = CompletableFuture.runAsync(new EpisodesRunnable(mogulId, podcast, entry.getValue()),
						this.executor);
				workQueuesCompletableFutures.add(cf);
			}
			CompletableFuture.allOf(workQueuesCompletableFutures.toArray(new CompletableFuture[0])).join();
		}
	}

	private Map<Integer, Collection<Episode>> divide(Collection<Episode> episodes) {
		var map = new ConcurrentHashMap<Integer, Collection<Episode>>();
		var counter = 0;
		for (var episode : episodes) {
			var k = counter % concurrency;
			map.computeIfAbsent(k, _ -> new HashSet<>()).add(episode);
			counter += 1;
		}
		return map;
	}

	class EpisodesRunnable implements Runnable {

		private final Long mogulId;

		private final Podcast podcast;

		private final Collection<Episode> episodes;

		EpisodesRunnable(Long mogulId, Podcast podcast, Collection<Episode> episodes) {
			this.episodes = episodes;
			this.podcast = podcast;
			this.mogulId = mogulId;
		}

		@Override
		public void run() {
			for (var ep : this.episodes) {
				ingestEpisode(podcast, ep, mogulId);
			}
		}

		private void ingestEpisode(Podcast podcast, Episode episode, Long mogulId) {

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
				searchService.index(segment);
			}
		}

	}

}
