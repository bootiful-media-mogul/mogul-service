package com.joshlong.mogul.api.blogs.publication;

import com.joshlong.mogul.api.PublisherPlugin;
import com.joshlong.mogul.api.blogs.Post;
import com.joshlong.mogul.api.utils.UriUtils;
import com.joshlong.mogul.api.wordpress.WordPressClient;
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

	private final WordPressClient wordPressClient;

	WordPressBlogPostPublisherPlugin(WordPressClient wordPressClient) {
		this.wordPressClient = wordPressClient;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	public Set<String> requiredSettingKeys() {
		return Set.of("authorizationUri", "tokenUri", "clientId", "clientSecret");
	}

	@Override
	public void publish(PublishContext<Post> publishContext) {

		var payload = publishContext.payload();
		var wordPressPostResponse = this.wordPressClient.publishPost(new WordPressPost(payload.title(),
				payload.content(), WordPressPost.Status.DRAFT, "", List.of(), List.of(), ""));
		// todo what's a slug? excerpt? those other values?
		Assert.hasText(wordPressPostResponse.link(), "WordPress post link cannot be empty");
		this.log.info("published post to WordPress at {}", wordPressPostResponse.link());
		publishContext.success(PLUGIN_NAME, UriUtils.uri(wordPressPostResponse.link()));

	}

	// todo should we make post == publish and then unpublish == draft ?
	@Override
	public boolean unpublish(UnpublishContext<Post> uc) {
		// todo implement this
		return false;
	}

}
