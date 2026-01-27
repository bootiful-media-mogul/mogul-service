package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.feeds.Entry;
import com.joshlong.mogul.api.feeds.EntryMapper;
import com.joshlong.mogul.api.feeds.Feeds;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Controller
@ResponseBody
class BlogPostFeedController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	// GET /public/feeds/moguls/16386/blogs/2/posts.atom

	private static final String BLOG_FEED_URL = "/public/feeds/moguls/{mogulId}/blogs/{blogId}/posts.atom";

	private final Feeds feeds;

	private final ManagedFileService managedFileService;

	private final MogulService mogulService;

	private final BlogService blogService;

	BlogPostFeedController(Feeds feeds, ManagedFileService managedFileService, MogulService mogulService,
			BlogService blogService) {
		this.feeds = feeds;
		this.managedFileService = managedFileService;
		this.mogulService = mogulService;
		this.blogService = blogService;
	}

	@GetMapping(value = BLOG_FEED_URL) // , produces =
										// MediaType.APPLICATION_ATOM_XML_VALUE)
	String feed(@PathVariable long mogulId, @PathVariable long blogId)
			throws IOException, ParserConfigurationException, TransformerException {
		this.log.debug("producing the RSS feed for " + BLOG_FEED_URL + " for mogulId {} and blogId {}", mogulId,
				blogId);
		var blogPostRowMapper = new BlogPostEntryMapper(Map.of());
		var mogul = this.mogulService.getMogulById(mogulId);
		var blog = this.blogService.getBlogById(blogId);
		Assert.state(blog.mogulId() == mogulId, "the blog's mogulId does not match the mogulId in the path");
		var posts = this.blogService.getPostsForBlog(blogId);
		return this.feeds.createMogulAtomFeed(blog.title(), BLOG_FEED_URL, blog.created().toInstant(),
				mogul.givenName() + " " + mogul.familyName(), longToUuid(blogId).toString(), posts, blogPostRowMapper);
	}

	private static UUID longToUuid(long id) {
		return new UUID(0, id);
	}

	private static class BlogPostEntryMapper implements EntryMapper<Post> {

		private final Map<Long, String> urls;

		private BlogPostEntryMapper(Map<Long, String> urls) {
			this.urls = urls;
		}

		@Override
		public Entry map(Post post) {
			var img = (Entry.Image) null;
			var uuid = longToUuid(post.id());
			var summary = post.summary();
			return new Entry(uuid.toString(), new Date(post.created().getTime()).toInstant(), post.title(),
					this.urls.get(post.id()), summary, Map.of("id", Long.toString(post.id())), img);
		}

	}

}
