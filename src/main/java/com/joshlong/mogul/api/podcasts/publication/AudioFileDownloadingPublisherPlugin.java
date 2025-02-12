package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.podcasts.Episode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * this plugin doesn't really 'publish' anything. it just lets the user download the
 * produced final audio file. nonetheless, it's useful to have the server-side validation
 * of the state of the episode here.
 *
 */

@Component(value = AudioFileDownloadingPublisherPlugin.PLUGIN_NAME)
class AudioFileDownloadingPublisherPlugin implements PodcastEpisodePublisherPlugin {

	static final String PLUGIN_NAME = "audioFile";

	private final Logger log = LoggerFactory.getLogger(getClass());

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
	}

	@Override
	public boolean unpublish(Map<String, String> context, Publication publication) {
		// noop - a user can't 'undownload' a file...
		return true;
	}

}
