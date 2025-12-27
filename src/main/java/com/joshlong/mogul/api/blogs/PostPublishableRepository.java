package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.AbstractPublishableRepository;
import org.springframework.stereotype.Component;

@Component
class PostPublishableRepository extends AbstractPublishableRepository<Post> {

	private final BlogService blogService;

	PostPublishableRepository(BlogService blogService) {
		super(Post.class);
		this.blogService = blogService;
	}

	@Override
	public Post find(Long id) {
		return this.blogService.getPostById(id);
	}

}
