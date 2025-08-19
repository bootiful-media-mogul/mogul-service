package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.podcasts.Episode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Set;

/**
 * this will initialize a new {@link com.joshlong.mogul.api.blogs.Post blog post} with the
 * contents of the transcript of a given {@link Episode podcast episode}. we can source
 * things like tags, titles, descriptions, etc., from the podcast, too.
 * <p>
 * this is the first plugin to require that the user provide some data upfront before it
 * can proceed. Namely: in which blog should the post be made?
 */

@Component(PodcastEpisodeBlogPostPublisherPlugin.PLUGIN_NAME)
class PodcastEpisodeBlogPostPublisherPlugin implements PodcastEpisodePublisherPlugin {

	public static final String PLUGIN_NAME = "podcastEpisodeToBlogPost";

	public static final String CONTEXT_BLOG_ID = "blogId";

	private final BlogService blogService;

	PodcastEpisodeBlogPostPublisherPlugin(BlogService blogService) {
		this.blogService = blogService;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public Set<String> requiredSettingKeys() {
		return Set.of(CONTEXT_BLOG_ID);
	}

	@Override
	public void publish(PublishContext<Episode> c) {
		var context = c.context();
		var blogId = Long.parseLong(context.get(CONTEXT_BLOG_ID));
		var targetBlog = blogService.getBlogById(blogId);
		Assert.notNull(targetBlog, "the blog must exist");
	}

	@Override
	public boolean unpublish(UnpublishContext<Episode> e) {
		return false;
	}

}
