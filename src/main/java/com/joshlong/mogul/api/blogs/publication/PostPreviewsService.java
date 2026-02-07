package com.joshlong.mogul.api.blogs.publication;

import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.utils.JdbcUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface PostPreviewsService {

	PostPreview createPostPreview(Long mogulId, Long postId);

	PostPreview getPostPreviewById(Long id);

}

@Service
@Transactional
class DefaultPostPreviewsService implements PostPreviewsService {

	private final JdbcClient db;

	private final ManagedFileService managedFileService;

	private final BlogService blogService;

	private final PostPreviewRowMapper pp = new PostPreviewRowMapper();

	DefaultPostPreviewsService(JdbcClient db, ManagedFileService managedFileService, BlogService blogService) {
		this.db = db;
		this.managedFileService = managedFileService;
		this.blogService = blogService;
	}

	@Override
	public PostPreview createPostPreview(Long mogulId, Long postId) {
		var mf = this.managedFileService.createManagedFile(mogulId, "blog-post-previews", Long.toString(postId), 0,
				MediaType.APPLICATION_OCTET_STREAM, true);
		var gkh = new GeneratedKeyHolder();
		this.db.sql("insert into blog_post_preview (mogul_id, blog_post_id, managed_file_id) values (?, ?, ?)") //
			.params(mogulId, postId, mf.id())
			.update(gkh);
		var previewId = JdbcUtils.getIdFromKeyHolder(gkh).longValue();
		return this.getPostPreviewById(previewId);
	}

	@Override
	public PostPreview getPostPreviewById(Long id) {
		return this.db.sql("select * from blog_post_preview where id = ?").param(id).query(this.pp).single();

	}

	private class PostPreviewRowMapper implements RowMapper<PostPreview> {

		@Override
		public PostPreview mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new PostPreview(rs.getLong("id"), blogService.getPostById(rs.getLong("blog_post_id")),
					managedFileService.getManagedFileById(rs.getLong("managed_file_id")));
		}

	}

}