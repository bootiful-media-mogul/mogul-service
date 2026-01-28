package com.joshlong.mogul.api.blogs.search;

import com.joshlong.mogul.api.EntityContext;
import com.joshlong.mogul.api.EntityContextBuilder;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.blogs.Post;
import com.joshlong.mogul.api.utils.TypeUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class PostEntityContextBuilder implements EntityContextBuilder<Post> {

	private final BlogService blogService;

	PostEntityContextBuilder(BlogService blogService) {
		this.blogService = blogService;
	}

	@Override
	public EntityContext buildContextFor(Long mogulId, Long entityId) {
		var post = this.blogService.getPostById(entityId);
		return new EntityContext(TypeUtils.typeName(Post.class), Map.of("postId", post.id(), "blogId", post.blogId()));
	}

}
