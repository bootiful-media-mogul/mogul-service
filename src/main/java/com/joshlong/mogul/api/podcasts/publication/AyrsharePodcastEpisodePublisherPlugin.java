package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.ayrshare.AyrshareConstants;
import com.joshlong.mogul.api.ayrshare.AyrshareService;
import com.joshlong.mogul.api.ayrshare.Platform;
import com.joshlong.mogul.api.podcasts.Episode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
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

	private final AyrshareService ayrshare;

	private final Logger log = LoggerFactory.getLogger(getClass());

	AyrsharePodcastEpisodePublisherPlugin(AyrshareService ayrshare) {
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
	public void publish(PublishContext<Episode> context) {

		// todo compositions, when we get them working, will be a natural fit with
		// ayrshare's media_urls capability
		this.log.debug("Ayrshare plugin got the following context: {} for the following episode {}", context,
				context.payload().toString());

		// if the user types in different text for each platform, then we'll publish a
		// different message for each
		// platform, which will involve multiple calls to Ayrshare. but if the text is the
		// same for all of them,
		// then we can detect that and simply call Ayrshare once with multiple target
		// platforms specified.

		var optimizedMapping = new HashMap<String, Set<Platform>>();
		for (var p : ayrshare.platforms()) {
			var key = p.platformCode().toLowerCase();
			if (context.context().containsKey(key)) {
				var post = context.context().get(key);
				optimizedMapping.computeIfAbsent(post.trim(), k -> new HashSet<>()).add(p);
			}
		}

		for (var post : optimizedMapping.keySet()) {
			var targets = optimizedMapping.getOrDefault(post, new HashSet<>()).toArray(new Platform[0]);
			if (targets.length > 0) {
				var response = this.ayrshare.post(post, targets);
				response.postIds().forEach((platform, postId) -> {
					context.outcome(platform.platformCode(), response.status().success(), postId.postUrl());
				});
			}
		}
	}

	@Override
	public boolean unpublish(UnpublishContext<Episode> context) {
		return false;
	}

}
