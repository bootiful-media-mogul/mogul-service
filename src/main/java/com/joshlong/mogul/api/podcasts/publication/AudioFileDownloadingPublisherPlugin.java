package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.podcasts.Episode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

	AudioFileDownloadingPublisherPlugin(ManagedFileService managedFileService) {
		this.managedFileService = managedFileService;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public boolean canPublish(PublishContext<Episode> context) {
		return PodcastEpisodePublisherPlugin.super.canPublish(context) && context.payload().complete();
	}

	@Override
	public Set<String> getRequiredSettingKeys() {
		return Set.of();
	}

	@Override
	public void publish(PublishContext<Episode> payload) {
		this.log.debug("downloading the produced audio file for episode # {}", payload.payload().id());
		this.managedFileService.setManagedFileVisibility(payload.payload().producedAudio().id(), true);
	}

	@Override
	public boolean unpublish(UnpublishContext<Episode> context) {
		this.log.debug("can't 'unpublish' a downloaded file for publication # {}", context.publication().id());
		return true;
	}

}
