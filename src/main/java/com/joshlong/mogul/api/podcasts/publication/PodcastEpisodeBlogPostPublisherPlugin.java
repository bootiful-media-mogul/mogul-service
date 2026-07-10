package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.blogs.Post;
import com.joshlong.mogul.api.blogs.PostCreationRequestedEvent;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.publications.PublicationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * this will initialize a new {@link Post blog post} with the contents of a given
 * {@link Episode podcast episode}. we can source things like tags, titles, descriptions,
 * etc., from the podcast, too.
 * <p>
 * this is the first plugin to require that the user provide some data upfront before it
 * can proceed. Namely: in which blog should the post be made?
 * <p>
 * rather than call {@code BlogService} directly (which would couple the {@code podcasts}
 * module to the {@code blogs} module through a service call), this plugin publishes a
 * {@link PostCreationRequestedEvent} that the blogs module listens for and acts upon.
 * this keeps the modules decoupled and keeps {@code ApplicationModules.verify()} happy.
 * the blogs listener runs synchronously and hands the created {@link Post} back so we can
 * record its path as a publication {@link PublishContext#outcomes() outcome}.
 */

@Component(PodcastEpisodeBlogPostPublisherPlugin.PLUGIN_NAME)
class PodcastEpisodeBlogPostPublisherPlugin implements PodcastEpisodePublisherPlugin {

	public static final String PLUGIN_NAME = "podcastEpisodeToBlogPost";

	public static final String CONTEXT_BLOG_ID = "blogId";

	private final ApplicationEventPublisher publisher;

	PodcastEpisodeBlogPostPublisherPlugin(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public Set<PublisherSetting> pluginSettings() {
		return Set.of(new PublisherSetting(true, CONTEXT_BLOG_ID));
	}

	@Override
	public boolean requiresProducedAudio() {
		// a blog post only needs the episode's title/description, so don't trigger the
		// (potentially long) audio production step just to create it.
		return false;
	}

	@Override
	public void publish(PublishContext<Episode> c) {
		var episode = c.payload();
		var mogulId = c.mogulId();
		var blogId = Long.parseLong(c.context().get(CONTEXT_BLOG_ID));
		var title = episode.title();
		var content = episode.description();
		var summary = "";
		var created = new AtomicReference<Post>();
		try {
			// the blogs module consumes this synchronously and hands back the new post,
			// so by the time publishEvent returns we know the post's identity.
			this.publisher
				.publishEvent(new PostCreationRequestedEvent(mogulId, blogId, title, content, summary, created::set));
		}
		catch (RuntimeException ex) {
			c.failure(PLUGIN_NAME, ex.getMessage());
			return;
		}
		var post = created.get();
		if (post == null) {
			c.failure(PLUGIN_NAME, "the blog post could not be created");
			return;
		}
		// surface a link to the new post in the publication outcomes so the UI can offer
		// it without redirecting. the outcome stores a java.net.URL (must be absolute),
		// so
		// we root the post path at the client base URL the gateway stamped on the
		// request;
		// if it's absent we still succeed, just without a link.
		var postPath = "/blogs/" + post.blogId() + "/posts/" + post.id();
		var baseUrl = c.context().get(PublicationService.BASE_URL);
		var url = StringUtils.hasText(baseUrl) ? URI.create(baseUrl.replaceAll("/+$", "") + postPath) : null;
		c.success(PLUGIN_NAME, url);
	}

	@Override
	public boolean unpublish(UnpublishContext<Episode> e) {
		return false;
	}

}
