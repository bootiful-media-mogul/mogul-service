package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.podcasts.Episode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static com.joshlong.mogul.api.podcasts.publication.MockPodcastEpisodePublisherPlugin.PLUGIN_NAME;

/**
 * this exists only to make it easier to debug the publication process.
 */
@Component(PLUGIN_NAME)
class MockPodcastEpisodePublisherPlugin implements PodcastEpisodePublisherPlugin {

	static final String PLUGIN_NAME = "mock";

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public boolean unpublish(UnpublishContext<Episode> episodeUnpublishContext) {
		return true;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public Set<String> getRequiredSettingKeys() {
		return Set.of();
	}

	@Override
	public boolean isConfigurationValid(Map<String, String> context) {
		return true;
	}

	@Override
	public boolean canPublish(PublishContext<Episode> episodePublishContext) {
		return true;
	}

	@Override
	public void publish(PublishContext<Episode> publishContext) {
		var payload = publishContext.payload();
		log.debug("start: publishing episode {} with context {}", payload.id(), publishContext.context());
		try {
			Thread.sleep(5_000);
		} //
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		log.debug("middle: publishing episode {} with context {}", payload.id(), publishContext.context());
		var count = (int) Math.max(Math.random() * 5.0, 2.0);
		for (var c = 0; c < count; c++) {
			publishContext.outcome("mock", Math.random() > .5, URI.create("https://spring.io"));
		}
		log.debug("stop: publishing episode {} with context {}", payload.id(), publishContext.context());
	}

}
