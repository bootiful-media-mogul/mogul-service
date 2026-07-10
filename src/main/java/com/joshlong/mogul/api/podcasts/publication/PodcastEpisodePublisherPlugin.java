package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.PublisherPlugin;
import com.joshlong.mogul.api.podcasts.Episode;

public interface PodcastEpisodePublisherPlugin extends PublisherPlugin<Episode> {

	/**
	 * whether this plugin needs the episode's produced (glued-together) audio. plugins
	 * that publish the episode itself (Podbean, audio download, etc.) do; plugins that
	 * only need the episode's metadata (e.g. turning it into a blog post) do not. when
	 * {@code false}, the lazy audio-production step in
	 * {@link ProducingPodcastPublisherPluginBeanPostProcessor} is skipped, so publishing
	 * doesn't trigger (a potentially long) {@code ffmpeg} render.
	 */
	default boolean requiresProducedAudio() {
		return true;
	}

}
