package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.podcasts.Episode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.joshlong.mogul.api.podcasts.publication.AyrsharePodcastEpisodePublisherPlugin.PLUGIN_NAME;

/**
 * this will show the user the possible social accounts they could target and, in
 * conjunction with the configuration in the settings page, allow the user to publish
 * messages. but, the client will need to send the content they want sent for each
 * destination. so this plugin needs to dynamically show compositions for each of the
 * final destinations. we'll need to support varyiong length content, references, etc.
 *
 * @author Josh Long
 */
@Component(value = PLUGIN_NAME)
class AyrsharePodcastEpisodePublisherPlugin implements PodcastEpisodePublisherPlugin {

	private static final String API_KEY = "ayrshareKey";

	static final String PLUGIN_NAME = "ayrshare";

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public Set<String> getRequiredSettingKeys() {
		return Set.of(API_KEY);
	}

	@Override
	public void publish(Map<String, String> context, Episode payload) {

	}

	@Override
	public boolean unpublish(Map<String, String> context, Publication publication) {
		return false;
	}

}
