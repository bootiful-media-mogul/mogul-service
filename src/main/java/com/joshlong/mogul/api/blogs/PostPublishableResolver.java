package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.AbstractPublishableResolver;
import org.springframework.stereotype.Component;

/**
 * resolves {@link com.joshlong.mogul.api.Publishable} instances of {@link Post}
 */
@Component
class PostPublishableResolver extends AbstractPublishableResolver<Post> {

	private final BlogService blogService;

	PostPublishableResolver(BlogService blogService) {
		super(Post.class);
		this.blogService = blogService;
	}

	@Override
	public Post find(Long id) {
		return this.blogService.getPostById(id);
	}

}
