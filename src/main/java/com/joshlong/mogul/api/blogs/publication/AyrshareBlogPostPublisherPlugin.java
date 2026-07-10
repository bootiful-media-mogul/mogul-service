package com.joshlong.mogul.api.blogs.publication;

import com.joshlong.mogul.api.ayrshare.AbstractAyrsharePublisherPlugin;
import com.joshlong.mogul.api.ayrshare.AyrshareService;
import com.joshlong.mogul.api.blogs.Post;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.settings.Settings;
import org.springframework.stereotype.Component;

import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.BLOG_POST_AYRSHARE_PLUGIN_NAME;
import static com.joshlong.mogul.api.blogs.publication.AyrshareBlogPostPublisherPlugin.NAME;

@Component(NAME)
class AyrshareBlogPostPublisherPlugin extends AbstractAyrsharePublisherPlugin<Post> implements BlogPostPublisherPlugin {

	static final String NAME = BLOG_POST_AYRSHARE_PLUGIN_NAME;

	AyrshareBlogPostPublisherPlugin(AyrshareService ayrshare, Settings settings, MogulService mogulService,
			ManagedFileService managedFileService) {
		super(NAME, ayrshare, settings, mogulService, managedFileService);
	}

}