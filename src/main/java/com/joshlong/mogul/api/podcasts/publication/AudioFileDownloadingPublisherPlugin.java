package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.podcasts.PodcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * this plugin doesn't really 'publish' anything. it just lets the user download the
 * produced final audio file. nonetheless, it's useful to have the server-side validation
 * of the state of the episode here.
 */
@Component(value = AudioFileDownloadingPublisherPlugin.PLUGIN_NAME)
class AudioFileDownloadingPublisherPlugin implements PodcastEpisodePublisherPlugin {

	static final String PLUGIN_NAME = "audioFile";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ManagedFileService managedFileService;

	private final PodcastService podcastService;

	AudioFileDownloadingPublisherPlugin(ManagedFileService managedFileService, PodcastService podcastService) {
		this.managedFileService = managedFileService;
		this.podcastService = podcastService;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public boolean canPublish(Map<String, String> context, Episode payload) {
		return PodcastEpisodePublisherPlugin.super.canPublish(context, payload) && payload.complete();
	}

	@Override
	public Set<String> getRequiredSettingKeys() {
		return Set.of();
	}

	@Override
	public void publish(Map<String, String> context, Episode payload) {
		this.log.debug("downloading the produced audio file for episode # {}", payload.id());
		// we need to ensure for all older files that the produced audio is downloadable
		// todo delete all of this!!! it shouldn't matter for anything but existing
		// episodes!
		this.managedFileService.setManagedFileVisibility(payload.producedAudio().id(), true);
		var podcastById = this.podcastService.getPodcastById(payload.podcastId());
		var mogulId = podcastById.mogulId();
		this.podcastService.getAllPodcastsByMogul(mogulId).forEach(podcast -> {
			this.podcastService.getPodcastEpisodesByPodcast(podcast.id()).forEach(ep -> {
				var produced = ep.producedAudio();
				if (produced != null && ep.complete()) {
					this.managedFileService.setManagedFileVisibility(produced.id(), true);
					System.out.println("setting the produced audio for episode #" + ep.id() + "to visible.");
				}
			});
		});

	}

	@Override
	public boolean unpublish(Map<String, String> context, Publication publication) {
		// noop - a user can't 'undownload' a file...
		return true;
	}

}
