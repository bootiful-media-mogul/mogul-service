package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.ayrshare.AyrshareConstants;
import com.joshlong.mogul.api.ayrshare.AyrshareService;
import com.joshlong.mogul.api.ayrshare.Platform;
import com.joshlong.mogul.api.compositions.Attachment;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.utils.UriUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/**
 * This will show the user the possible social accounts they could target and, in
 * conjunction with the configuration in the settings page, allow the user to publish
 * messages. The client will need to send the content they want sent for each destination.
 * this plugin needs to dynamically show compositions for each of the final destinations.
 * We support varying lengths of message content, platform user references, media
 * attachments, etc.
 *
 * @author Josh Long
 */
@Component(value = AyrshareConstants.PLUGIN_NAME)
class AyrsharePodcastEpisodePublisherPlugin implements PodcastEpisodePublisherPlugin {

	private final AyrshareService ayrshare;

	private final MogulService mogulService;

	private final ManagedFileService managedFileService;

	AyrsharePodcastEpisodePublisherPlugin(AyrshareService ayrshare, MogulService mogulService,
			ManagedFileService managedFileService) {
		this.ayrshare = ayrshare;
		this.mogulService = mogulService;
		this.managedFileService = managedFileService;
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
		var mogul = this.mogulService.getCurrentMogul();
		var drafts = this.ayrshare.getDraftAyrsharePublicationCompositionsFor(mogul.id());
		var platformsToCompositions = new HashMap<String, Collection<Attachment>>();
		for (var c : drafts) {
			platformsToCompositions.put(c.platform().platformCode(), c.composition().attachments());
		}
		for (var platform : ayrshare.platforms()) {
			var platformKey = platform.platformCode().toLowerCase();
			if (context.context().containsKey(platformKey)) {
				var post = context.context().get(platformKey);
				var response = ayrshare.post(post, new Platform[] { platform }, postContext -> {
					var media = platformsToCompositions.getOrDefault(platform.platformCode(), Set.of());
					var uris = media //
						.stream() //
						.map(attachment -> {
							var managedFile = attachment.managedFile();
							return UriUtils.uri(managedFileService.getPublicUrlForManagedFile(managedFile.id()));
						}) //
						.toArray(URI[]::new);
					postContext.media(uris);
				});
				response.postIds()
					.forEach((platformObj, posted) -> context.success(platformObj.platformCode().toLowerCase(),
							posted.postUrl()));
			}
		}
	}

	@Override
	public boolean unpublish(UnpublishContext<Episode> context) {
		return false;
	}

}
