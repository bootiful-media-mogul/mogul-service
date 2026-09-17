package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Publishable;
import com.joshlong.mogul.api.PublisherPlugin;
import com.joshlong.mogul.api.compositions.Attachment;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.settings.Settings;
import com.joshlong.mogul.api.utils.UriUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.*;

/**
 * Publish the contents of compositions to the Ayrshare platform.
 */
public abstract class AbstractAyrsharePublisherPlugin<T extends Publishable> implements PublisherPlugin<T> {

	private final String name;

	private final AyrshareService ayrshare;

	private final Settings settings;

	private final MogulService mogulService;

	private final ManagedFileService managedFileService;

	public AbstractAyrsharePublisherPlugin(String name, AyrshareService ayrshare, Settings settings,
			MogulService mogulService, ManagedFileService managedFileService) {
		this.name = name;
		this.ayrshare = ayrshare;
		this.settings = settings;
		this.mogulService = mogulService;
		this.managedFileService = managedFileService;
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public Set<PublisherSetting> pluginSettings() {
		return Set.of(new PublisherSetting(true, API_KEY_SETTING_KEY),
				new PublisherSetting(false, TWITTER_OAUTH1_API_KEY),
				new PublisherSetting(false, TWITTER_OAUTH1_API_SECRET));
	}

	@Override
	public void publish(PublishContext<T> context) {
		var mogul = this.mogulService.getCurrentMogul();
		var drafts = this.ayrshare.getDraftAyrsharePublicationCompositionsFor(mogul.id());
		var platformsToCompositions = new HashMap<String, Collection<Attachment>>();
		for (var c : drafts) {
			platformsToCompositions.put(c.platform().platformCode(), c.composition().attachments());
		}
		for (var platform : this.ayrshare.platforms()) {
			var platformKey = platform.platformCode().toLowerCase();
			var pluginExecutionContext = context.context();
			if (pluginExecutionContext.containsKey(platformKey)) {
				var post = pluginExecutionContext.get(platformKey);
				var response = this.ayrshare.post(post, new Platform[] { platform }, () -> {
					var settingsForTenant = settings.getAllSettingsByCategory(context.mogulId(), this.name);
					return settingsForTenant.get(API_KEY_SETTING_KEY).value();
				}, postContext -> {
					var media = platformsToCompositions.getOrDefault(platform.platformCode(), Set.of());
					var uris = media //
						.stream() //
						.map(attachment -> {
							var managedFile = attachment.managedFile();
							return UriUtils.uri(managedFileService.getPublicUrlForManagedFile(managedFile.id()));
						}) //
						.toArray(URI[]::new);
					postContext.media(uris);
					if (platform.equals(Platform.X)) {
						var apiKey = pluginExecutionContext.get(TWITTER_OAUTH1_API_KEY);
						var apiSecret = pluginExecutionContext.get(TWITTER_OAUTH1_API_SECRET);
						var hasValidTwitterCredentials = StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret);
						if (hasValidTwitterCredentials) {
							var customHeaders = Map.of( //
									"X-Twitter-OAuth1-Api-Key", apiKey, //
									"X-Twitter-OAuth1-Api-Secret", apiSecret //
							);
							postContext.customHeaders(customHeaders);
						} //
						else {
							context.failure(platform.platformCode().toLowerCase(),
									"you didn't specify the twitter oauth key and secret in the settings page!", post);
						}
					}
				});
				// the post text doubles as the outcome's preview: a link to a tweet says
				// nothing about which tweet, and these go out one per platform with
				// per-platform text, so the link alone leaves the user guessing.
				response.postIds()
					.forEach((platformObj, posted) -> context.success(platformObj.platformCode().toLowerCase(),
							posted.postUrl(), post));
			}
		}
	}

	@Override
	public boolean unpublish(UnpublishContext<T> uc) {
		return false;
	}

}
