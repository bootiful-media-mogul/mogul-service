package com.joshlong.mogul.api.blogs.search;

import com.joshlong.mogul.api.AbstractSearchableResolver;
import com.joshlong.mogul.api.SearchableResult;
import com.joshlong.mogul.api.blogs.Blog;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.blogs.Post;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.*;

@Component
class PostSearchableResolver extends AbstractSearchableResolver<Post> {

	private final BlogService blogService;

	PostSearchableResolver(BlogService blogService) {
		super(Post.class);
		this.blogService = blogService;
	}

	@Override
	public List<SearchableResult<Post>> results(List<Long> searchableIds) {
		if (searchableIds == null || searchableIds.isEmpty())
			return Collections.emptyList();
		// todo refactor this to use a more efficient method!
		var posts = searchableIds.stream().map(this.blogService::getPostById).toList();
		var searchableResults = new ArrayList<SearchableResult<Post>>();
		Assert.notEmpty(posts, "Posts must not be empty");
		for (var post : posts) {
			searchableResults.add(this.buildResultFor(post, post.content()));
		}
		return searchableResults;
	}

	private SearchableResult<Post> buildResultFor(Post post, String transcript) {
		return new SearchableResult<>(post.searchableId(), post, post.title(), transcript, post.id(), post.created(),
				this.type);
	}

	@Override
	public Post find(Long key) {
		return this.blogService.getPostById(key);
	}

}
