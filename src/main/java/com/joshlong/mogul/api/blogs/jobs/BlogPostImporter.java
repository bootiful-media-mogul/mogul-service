package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.archives.ArchiveExtractor;
import com.joshlong.mogul.api.archives.ArchiveFile;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.BufferedInputStream;
import java.util.Map;

@Component
class BlogPostImporter {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ManagedFileService managedFileService;

	private final BlogService blogService;

	private final ArchiveExtractor archiveExtractor;

	BlogPostImporter(ManagedFileService managedFileService, BlogService blogService,
			ArchiveExtractor archiveExtractor) {
		this.managedFileService = managedFileService;
		this.blogService = blogService;
		this.archiveExtractor = archiveExtractor;
	}

	void importBlogPostsFromArchive(long mogulId, long blogId, long managedFileId) throws Exception {
		var archive = this.managedFileService.getManagedFileById(managedFileId);
		Assert.notNull(archive, "managed file id " + managedFileId + " not found");
		var blog = this.blogService.getBlogById(blogId);
		Assert.notNull(blog, "blog id " + blogId + " not found");
		var resourceForArchive = this.managedFileService.read(managedFileId);
		try (var bufferedInputStream = new BufferedInputStream(resourceForArchive.getInputStream());) {
			var mediaType = CommonMediaTypes.guess(bufferedInputStream);
			Assert.state(CommonMediaTypes.isArchive(mediaType), "archive is invalid");
			var data = JsonUtils.write(Map.of("jobName", getClass().getName(), //
					"mogul", mogulId, //
					"blog", blog.id(), //
					"managedFile", managedFileId //
			));
			this.log.info(data);
			this.archiveExtractor.extract(bufferedInputStream, it -> this.ingest(mogulId, blogId, it));
		}
	}

	private void ingest(long mogulId, long blogId, ArchiveFile archiveFile) {

		this.log.info("ingesting {}/{} for {}", mogulId, blogId, archiveFile);

	}

}
