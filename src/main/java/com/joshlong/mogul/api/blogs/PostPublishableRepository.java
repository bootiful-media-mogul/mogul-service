package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.PublishableRepository;
import org.springframework.stereotype.Component;

@Component
class PostPublishableRepository implements PublishableRepository<Post> {

	private final BlogService blogService;

	PostPublishableRepository(BlogService blogService) {
		this.blogService = blogService;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return Post.class.isAssignableFrom(clazz);
	}

	@Override
	public Post find(Long id) {
		return this.blogService.getPostById(id);
	}

}
