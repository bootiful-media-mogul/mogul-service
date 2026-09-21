package com.joshlong.mogul.api.blogs.publication;

import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.utils.CollectionUtils;
import com.joshlong.mogul.utils.JdbcUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public interface PostPreviewsService {

	PostPreview createPostPreview(Long mogulId, Long publicationId, Long postId);

	PostPreview getPostPreviewById(Long id);

}

@Service
@Transactional
class DefaultPostPreviewsService implements PostPreviewsService {

	private final JdbcClient db;

	private final ManagedFileService managedFileService;

	private final BlogService blogService;

	private final PostPreviewResultSetExtractor postPreviewResultSetExtractor = new PostPreviewResultSetExtractor();

	DefaultPostPreviewsService(JdbcClient db, ManagedFileService managedFileService, BlogService blogService) {
		this.db = db;
		this.managedFileService = managedFileService;
		this.blogService = blogService;
	}

	@Override
	public PostPreview createPostPreview(Long mogulId, Long publicationId, Long postId) {
		var mf = this.managedFileService.createManagedFile(mogulId, "blog-post-publication-previews",
				Long.toString(postId), 0, MediaType.APPLICATION_OCTET_STREAM, true);
		var gkh = new GeneratedKeyHolder();
		this.db.sql(
				"insert into blog_post_publication_preview(mogul_id, publication_id, blog_post_id, managed_file_id) values (?,?,?,?)") //
			.params(mogulId, publicationId, postId, mf.id())
			.update(gkh);
		var previewId = JdbcUtils.getIdFromKeyHolder(gkh).longValue();
		return this.getPostPreviewById(previewId);
	}

	@Override
	public PostPreview getPostPreviewById(Long id) {
		return CollectionUtils.firstOrNull(this.db.sql("select * from blog_post_publication_preview where id = ?")
			.param(id)
			.query(this.postPreviewResultSetExtractor));
	}

	/**
	 * one row, read but not yet resolved: the post and the managed file it points at are
	 * fetched for the whole batch once every row is in hand.
	 */
	private record PostPreviewRow(Long publicationId, Long id, Long postId, Long managedFileId) {
	}

	/**
	 * a preview points at both a post and a managed file, and resolving those a row at a
	 * time would be two queries per row. only one row is ever asked for today, which is
	 * the only reason that has not cost anything -- so this reads the rows first and
	 * resolves both sets in one call each, and stays correct the day something asks for a
	 * list of them.
	 */
	private class PostPreviewResultSetExtractor implements ResultSetExtractor<List<PostPreview>> {

		@Override
		public List<PostPreview> extractData(ResultSet rs) throws SQLException, DataAccessException {
			var rows = new ArrayList<PostPreviewRow>();
			var postIds = new HashSet<Long>();
			var managedFileIds = new HashSet<Long>();
			while (rs.next()) {
				var row = new PostPreviewRow(rs.getLong("publication_id"), rs.getLong("id"), rs.getLong("blog_post_id"),
						rs.getLong("managed_file_id"));
				rows.add(row);
				postIds.add(row.postId());
				managedFileIds.add(row.managedFileId());
			}
			if (rows.isEmpty())
				return List.of();
			var posts = blogService.getPostsByIds(postIds);
			var managedFiles = managedFileService.getManagedFiles(managedFileIds);
			var results = new ArrayList<PostPreview>();
			for (var row : rows)
				results.add(new PostPreview(row.publicationId(), row.id(), posts.get(row.postId()),
						managedFiles.get(row.managedFileId())));
			return results;
		}

	}

}