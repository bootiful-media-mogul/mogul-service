package com.joshlong.mogul.api.podcasts.search;

/*
 *
 * @Component
 *
 * @Transactional class PodcastIndexer {
 *
 * private final SearchService searchService;
 *
 * private final PodcastService podcastService;
 *
 * PodcastIndexer(SearchService searchService, PodcastService podcastService) {
 * this.searchService = searchService; this.podcastService = podcastService; }
 *
 * @ApplicationModuleListener void podcastCompleted(PodcastEpisodeCompletedEvent event) {
 *
 * if (!event.episode().complete()) return;
 *
 * var episode = event.episode();
 *
 * for (var segment :
 * this.podcastService.getPodcastEpisodeSegmentsByEpisode(episode.id())) {
 * this.searchService.index(segment); } }
 *
 * }
 */
