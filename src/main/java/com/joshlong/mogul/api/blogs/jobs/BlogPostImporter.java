package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.archives.ArchiveExtractor;
import com.joshlong.mogul.api.archives.Tgz;
import com.joshlong.mogul.api.archives.Zip;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
class BlogPostImporter {

	private final ManagedFileService managedFileService;

	private final BlogService blogService;

	private final ArchiveExtractor zips;

	private final ArchiveExtractor tgzs;

	BlogPostImporter(ManagedFileService managedFileService, BlogService blogService, @Zip ArchiveExtractor zips,
			@Tgz ArchiveExtractor tgzs) {
		this.managedFileService = managedFileService;
		this.blogService = blogService;
		this.zips = zips;
		this.tgzs = tgzs;
	}

	void importBlogPostsFromArchive(long blogId, long managedFileId) {
		var archive = this.managedFileService.getManagedFileById(managedFileId);
		Assert.notNull(archive, "managed file id " + managedFileId + " not found");
		var blog = this.blogService.getBlogById(blogId);
		Assert.notNull(blog, "blog id " + blogId + " not found");
		var resourceForArchive = this.managedFileService.read(managedFileId);
		var mediaType = CommonMediaTypes.guess(resourceForArchive);
		Assert.state(CommonMediaTypes.isArchive(mediaType), "archive is invalid");

	}

}
