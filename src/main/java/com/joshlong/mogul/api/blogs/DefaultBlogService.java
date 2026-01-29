package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.ai.AiClient;
import com.joshlong.mogul.api.compositions.Composition;
import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.utils.JdbcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Transactional
class DefaultBlogService implements BlogService {

	public final ApplicationEventPublisher publisher;

	private final RowMapper<Blog> blogRowMapper = new BlogRowMapper();

	private final CompositionService compositionService;

	private final RowMapper<Post> postRowMapper = new PostRowMapper();

	private final JdbcClient db;

	private final AiClient singularity;

	private final Logger log = LoggerFactory.getLogger(getClass());

	DefaultBlogService(JdbcClient db, AiClient singularity, ApplicationEventPublisher publisher,
			CompositionService compositionService) {
		this.db = db;
		this.singularity = singularity;
		this.publisher = publisher;
		this.compositionService = compositionService;
	}

	@Override
	public Collection<Blog> getBlogsFor(long mogulId) {
		var all = this.db //
			.sql("select * from blog where mogul_id = ? ") //
			.params(mogulId) //
			.query(this.blogRowMapper) //
			.list();
		for (var a : all)
			this.log.info("found blog {}", a);
		return all;
	}

	@Override
	public Collection<Post> getPostsForBlog(long blogId) {
		return this.db //
			.sql("select * from blog_post where blog_id = ? ") //
			.params(blogId) //
			.query(this.postRowMapper) //
			.list();
	}

	@Override
	public Blog createBlog(Long mogulId, String title, String description) {
		var generatedKeyHolder = new GeneratedKeyHolder();
		this.db //
			.sql("""
						insert into blog(mogul_id , title , description)
						values (?,?,?)
						on conflict on constraint blog_mogul_id_title_key do update
						set title = excluded.title, description = excluded.description
					""")//
			.params(mogulId, title, description) //
			.update(generatedKeyHolder);
		var id = JdbcUtils.getIdFromKeyHolder(generatedKeyHolder);
		var blog = this.getBlogById(id.longValue());
		this.publisher.publishEvent(new BlogCreatedEvent(blog));

		return blog;
	}

	@Override
	public Blog updateBlog(Long mogulId, Long blogId, String title, String description) {
		// mogul
		this.db.sql("update blog set title = ?, description = ? where id = ? and mogul_id = ? ") //
			.params(title, description, blogId, mogulId)//
			.update();
		//
		var blog = this.getBlogById(blogId);
		this.publisher.publishEvent(new BlogUpdatedEvent(blog));
		return blog;
	}

	@Override
	public Blog getBlogById(Long id) {
		return this.db //
			.sql("select * from blog where id =? ") //
			.params(id) //
			.query(this.blogRowMapper) //
			.single();
	}

	@Override
	public Post getPostById(Long id) {
		var map = this.getPostsByIds(List.of(id));
		if (map.size() != 1)
			throw new IllegalStateException("there should be only one result");
		return map.get(map.keySet().iterator().next());
	}

	@Override
	public Map<Long, Post> getPostsByIds(Collection<Long> ids) {
		var map = new HashMap<Long, Post>();
		var list = this.db //
			.sql(" select * from blog_post where id = any(?) ") //
			.params(new SqlArrayValue("bigint", ids.toArray())) //
			.query(this.postRowMapper) //
			.list();
		for (var a : list) {
			map.put(a.id(), a);
		}
		return map;

	}

	@Override
	public void deleteBlog(Long id) {
		if (this.getBlogById(id) != null) {
			this.log.info("deleting blog {}", id);
			// todo queue up all the ManagedFiles associated with this blog for deletion
			this.db.sql("delete from blog_post where blog_id = ? ").params(id).update();
			this.db.sql("delete from blog where id = ? ").params(id).update();
		}
	}

	@Override
	public String summarize(String content) {
		var prompt = """
				Please summarize the text following the line. Reply with only the summary, and no other text:
				================================================================================================

				%s

				""";
		return this.singularity //
			.chat(prompt.formatted(content)) //
			.trim();
	}

	@Override
	public Post updatePost(Long postId, String title, String content, String summary) {
		this.db.sql("update  blog_post set title = ? , content = ?, summary = ? where id = ?")
			.params(title, content, summary, postId)
			.update();
		var postById = this.getPostById(postId);
		this.publisher.publishEvent(new PostUpdatedEvent(postById));
		return postById;
	}

	private Composition getDescriptionComposition(Long postId) {
		return this.compositionFor(postId, "description");
	}

	private Composition compositionFor(Long postId, String field) {
		var episode = this.getPostById(postId);
		return this.compositionService.compose(episode, field);
	}

	@Override
	public Post createPost(Long blogId, String title, String content, String summary) {
		var gkh = new GeneratedKeyHolder();
		this.db.sql(" insert into blog_post(blog_id, title, content ,summary ) values (?,?,?,?) ")
			.params(blogId, title, content, summary)
			.update(gkh);
		var id = JdbcUtils.getIdFromKeyHolder(gkh).longValue();
		var post = this.getPostById(id);
		var descriptionComposition = this.getDescriptionComposition(id);
		Assert.notNull(descriptionComposition, "description is null");
		this.publisher.publishEvent(new PostCreatedEvent(post));
		return post;
	}

	@Override
	public Map<String, ManagedFile> resolveAssetsForPost(Long postId) {
		return Map.of();
	}

	@ApplicationModuleListener
	void onPostUpdatedEvent(PostUpdatedEvent postUpdatedEvent) {

	}

	@Override
	public Composition getBlogPostDescriptionComposition(Long postId) {
		return this.getDescriptionComposition(postId);
	}

	private static class PostRowMapper implements RowMapper<Post> {

		@Override
		public Post mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new Post(rs.getLong("blog_id"), rs.getLong("id"), rs.getString("title"), rs.getDate("created"),
					rs.getString("content"), rs.getBoolean("complete"), new HashMap<>(), rs.getString("summary"),
					rs.getLong("blog_id"));
		}

	}

	private static class BlogRowMapper implements RowMapper<Blog> {

		@Override
		public Blog mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new Blog(rs.getLong("mogul_id"), rs.getLong("id"), rs.getString("title"),
					rs.getString("description"), rs.getTimestamp("created"));
		}

	}

}
