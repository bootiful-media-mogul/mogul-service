package com.joshlong.mogul.api.blogs.publication;

import com.joshlong.mogul.api.PublisherPlugin;
import com.joshlong.mogul.api.blogs.Post;
import com.joshlong.mogul.api.utils.UriUtils;
import com.joshlong.mogul.api.wordpress.WordPressDotComClient;
import com.joshlong.mogul.api.wordpress.WordPressPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Set;

@Component(value = WordPressBlogPostPublisherPlugin.PLUGIN_NAME)
class WordPressBlogPostPublisherPlugin implements PublisherPlugin<Post> {

	static final String PLUGIN_NAME = "wordpress";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final WordPressDotComClient wordPressDotComClient;

	WordPressBlogPostPublisherPlugin(WordPressDotComClient wordPressDotComClient) {
		this.wordPressDotComClient = wordPressDotComClient;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public Set<String> requiredSettingKeys() {
		// todo the base URL for the wordpress instance,
		// the client id and the client secret should all
		// be required configuration
		return Set.of();
	}

	@Override
	public void publish(PublishContext<Post> publishContext) {

		var payload = publishContext.payload();
		var wordPressPostResponse = this.wordPressDotComClient.publishPost(new WordPressPost(payload.title(),
				payload.content(), WordPressPost.Status.DRAFT, "", List.of(), List.of(), ""));

		Assert.hasText(wordPressPostResponse.link(), "WordPress post link cannot be empty");
		this.log.info("published post to WordPress at {}", wordPressPostResponse.link());
		publishContext.success(PLUGIN_NAME, UriUtils.uri(wordPressPostResponse.link()));

	}

	@Override
	public boolean unpublish(UnpublishContext<Post> uc) {
		// todo implement this
		return false;
	}

}
