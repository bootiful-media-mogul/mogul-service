package com.joshlong.mogul.api.blogs.publication;

import com.joshlong.mogul.api.ayrshare.AyrshareConstants;
import com.joshlong.mogul.api.ayrshare.AyrshareService;
import com.joshlong.mogul.api.ayrshare.Platform;
import com.joshlong.mogul.api.blogs.Post;
import com.joshlong.mogul.api.compositions.Attachment;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.settings.Settings;
import com.joshlong.mogul.api.utils.UriUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.*;

@Component(value = AyrshareBlogPostPublisherPlugin.NAME)
class AyrshareBlogPostPublisherPlugin implements BlogPostPublisherPlugin {

	static final String NAME = BLOG_POST_AYRSHARE_PLUGIN_NAME;

	private final AyrshareService ayrshare;

	private final MogulService mogulService;

	private final ManagedFileService managedFileService;

	private final Settings settings;

	AyrshareBlogPostPublisherPlugin(AyrshareService ayrshare, MogulService mogulService,
			ManagedFileService managedFileService, Settings settings) {
		this.ayrshare = ayrshare;
		this.mogulService = mogulService;
		this.managedFileService = managedFileService;
		this.settings = settings;
	}

	@Override
	public Set<PublisherSetting> pluginSettings() {
		return Set.of(new PublisherSetting(true, API_KEY_SETTING_KEY),
				new PublisherSetting(false, TWITTER_OAUTH1_API_KEY),
				new PublisherSetting(false, TWITTER_OAUTH1_API_SECRET));
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public void publish(PublishContext<Post> context) {
		var mogul = this.mogulService.getCurrentMogul();
		var ayrshareSettings = this.settings.getAllValuesByCategory(mogul.id(), NAME);
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

					var settingsForTenant = settings.getAllSettingsByCategory(context.mogulId(), NAME);
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
						var customHeaders = Map.of( //
								"X-Twitter-OAuth1-Api-Key", ayrshareSettings.get(TWITTER_OAUTH1_API_KEY), //
								"X-Twitter-OAuth1-Api-Secret", ayrshareSettings.get(TWITTER_OAUTH1_API_SECRET) //
						);
						postContext.customHeaders(customHeaders);
					}
				});
				response.postIds()
					.forEach((platformObj, posted) -> context.success(platformObj.platformCode().toLowerCase(),
							posted.postUrl()));
			}
		}
	}

	@Override
	public boolean unpublish(UnpublishContext<Post> context) {
		return false;
	}

}
