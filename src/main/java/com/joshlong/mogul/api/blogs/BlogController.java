package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.compositions.Composition;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Controller;

import java.time.OffsetDateTime;
import java.util.Collection;

@Controller
class BlogController {

	private final BlogService service;

	private final MogulService mogulService;

	BlogController(BlogService service, MogulService mogulService) {
		this.service = service;
		this.mogulService = mogulService;
	}

	@MutationMapping
	Post createPost(@Argument Long blogId, @Argument String title, @Argument String content, @Argument String summary) {
		return this.service.createPost(blogId, title, content, summary);
	}

	@MutationMapping
	boolean deleteBlog(@Argument Long blogId) {
		this.service.deleteBlog(blogId);
		return true;
	}

	@MutationMapping
	boolean deletePost(@Argument Long postId) {
		try {
			this.service.deletePost(postId);
		} //
		catch (Throwable throwable) {
			this.log.error("Error while deleting post", throwable);
		}
		return true;
	}

	private final Logger log = LoggerFactory.getLogger(BlogController.class);

	@SchemaMapping
	Composition descriptionComposition(Post post) {
		return this.service.getBlogPostDescriptionComposition(post.id());
	}

	@QueryMapping
	Collection<Post> blogPostsByBlog(@Argument Long blogId) {
		return this.service.getPostsForBlog(blogId);
	}

	@MutationMapping
	boolean updatePost(@Argument Long postId, @Argument String title, @Argument String description,
			@Argument String summary) {
		this.service.updatePost(postId, title, description, summary);
		return true;
	}

	@MutationMapping
	String summarize(@Argument String content) {
		return this.service.summarize(content);
	}

	@QueryMapping
	Post postById(@Argument Long postId) {
		return this.service.getPostById(postId);
	}

	@SchemaMapping
	OffsetDateTime created(Post blog) {
		return DateUtils.forDate(blog.created());
	}

	@ApplicationModuleListener
	void blogCreatedEventNotifyingListener(BlogCreatedEvent event) {
		var ne = NotificationEvent.visibleNotificationEventFor(event.blog().mogulId(), event,
				Long.toString(event.blog().id()), event.blog().title());
		NotificationEvents.notify(ne);
	}

	@ApplicationModuleListener
	void blogDeletedEventNotifyingListener(BlogDeletedEvent event) {
		var ne = NotificationEvent.visibleNotificationEventFor(event.blog().mogulId(), event,
				Long.toString(event.blog().id()), event.blog().title());
		NotificationEvents.notify(ne);
	}

	@ApplicationModuleListener
	void blogUpdatedEventNotifyingListener(BlogUpdatedEvent podcastUpdatedEvent) {
		var ne = NotificationEvent.visibleNotificationEventFor(podcastUpdatedEvent.blog().mogulId(),
				podcastUpdatedEvent, Long.toString(podcastUpdatedEvent.blog().id()),
				podcastUpdatedEvent.blog().title());
		NotificationEvents.notify(ne);
	}

	@MutationMapping
	boolean updateBlog(@Argument Long blogId, @Argument String title, @Argument String description) {
		this.service.updateBlog(this.mogulService.getCurrentMogul().id(), blogId, title, description);
		return true;
	}

	@MutationMapping
	Blog createBlog(@Argument String title, @Argument String description) {
		var mogul = this.mogulService.getCurrentMogul();
		return this.service.createBlog(mogul.id(), title, description);
	}

	@QueryMapping
	Blog blogById(@Argument Long blogId) {
		return this.service.getBlogById(blogId);
	}

	@SchemaMapping
	OffsetDateTime created(Blog blog) {
		return DateUtils.forDate(blog.created());
	}

	@QueryMapping
	Collection<Blog> blogs() {
		var mogul = this.mogulService.getCurrentMogul();
		return this.service.getBlogsFor(mogul.id());
	}

}
