package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.ayrshare.Ayrshare;
import com.joshlong.mogul.api.ayrshare.AyrshareConstants;
import com.joshlong.mogul.api.podcasts.Episode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * this will show the user the possible social accounts they could target and, in
 * conjunction with the configuration in the settings page, allow the user to publish
 * messages. but, the client will need to send the content they want sent for each
 * destination. so this plugin needs to dynamically show compositions for each of the
 * final destinations. we'll need to support varyiong length content, references, etc.
 *
 * @author Josh Long
 */
@Component(value = AyrshareConstants.PLUGIN_NAME)
class AyrsharePodcastEpisodePublisherPlugin implements PodcastEpisodePublisherPlugin {

	private final Ayrshare ayrshare;

	private final Logger log = LoggerFactory.getLogger(getClass());

	AyrsharePodcastEpisodePublisherPlugin(Ayrshare ayrshare) {
		this.ayrshare = ayrshare;
	}

	@Override
	public String name() {
		return AyrshareConstants.PLUGIN_NAME;
	}

	@Override
	public Set<String> getRequiredSettingKeys() {
		return Set.of(AyrshareConstants.API_KEY_SETTING_KEY);
	}

	@Override
	public void publish(Map<String, String> context, Episode payload) {

		this.log.debug("Ayrshare plugin got the following context: {} for the following episode {}", context,
				payload.toString());

	}

	@Override
	public boolean unpublish(Map<String, String> context, Publication publication) {
		return false;
	}

}
