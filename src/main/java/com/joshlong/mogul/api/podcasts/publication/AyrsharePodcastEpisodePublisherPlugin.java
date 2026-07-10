package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.ayrshare.AbstractAyrsharePublisherPlugin;
import com.joshlong.mogul.api.ayrshare.AyrshareService;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.settings.Settings;
import org.springframework.stereotype.Component;

import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.PODCAST_EPISODE_AYRSHARE_PLUGIN_NAME;
import static com.joshlong.mogul.api.podcasts.publication.AyrsharePodcastEpisodePublisherPlugin.NAME;

@Component(NAME)
class AyrsharePodcastEpisodePublisherPlugin extends AbstractAyrsharePublisherPlugin<Episode>
		implements PodcastEpisodePublisherPlugin {

	static final String NAME = PODCAST_EPISODE_AYRSHARE_PLUGIN_NAME;

	AyrsharePodcastEpisodePublisherPlugin(AyrshareService ayrshare, Settings settings, MogulService mogulService,
			ManagedFileService managedFileService) {
		super(NAME, ayrshare, settings, mogulService, managedFileService);
	}

}