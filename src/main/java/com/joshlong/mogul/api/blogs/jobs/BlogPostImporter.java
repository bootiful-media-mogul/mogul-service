package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.archives.ArchiveExtractor;
import com.joshlong.mogul.api.archives.ArchiveFile;
import com.joshlong.mogul.api.archives.Consumers;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.BufferedInputStream;

@Component
class BlogPostImporter {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ManagedFileService managedFileService;

	private final BlogService blogService;

	private final ArchiveExtractor archiveExtractor;

	private final MarkdownDocuments markdownDocuments;

	BlogPostImporter(ManagedFileService managedFileService, BlogService blogService, ArchiveExtractor archiveExtractor,
			MarkdownDocuments markdownDocuments) {
		this.managedFileService = managedFileService;
		this.markdownDocuments = markdownDocuments;
		this.blogService = blogService;
		this.archiveExtractor = archiveExtractor;
	}

	/* testing */
	void importBlogPostsFromArchive(long mogulId, long blogId, long managedFileId) throws Exception {
		var archive = this.managedFileService.getManagedFileById(managedFileId);
		Assert.notNull(archive, "managed file id " + managedFileId + " not found");
		var blog = this.blogService.getBlogById(blogId);
		Assert.notNull(blog, "blog id " + blogId + " not found");
		var resourceForArchive = this.managedFileService.read(managedFileId);
		try (var bufferedInputStream = new BufferedInputStream(resourceForArchive.getInputStream())) {
			var mediaType = CommonMediaTypes.guess(bufferedInputStream);
			Assert.state(CommonMediaTypes.isArchive(mediaType), "archive is invalid");
			this.archiveExtractor.extract(bufferedInputStream,
					Consumers.readableTextOnly(it -> this.ingest(mogulId, blogId, it)));
		}
	}

	private void debug(MarkdownDocument markdownDocument) {
		var thickLine = "=========";
		var smallLine = "---------";
		var content = new StringBuilder();
		var nl = System.lineSeparator();
		content.append(nl).append(thickLine).append(nl);
		markdownDocument.header().rawHeader().forEach((k, v) -> content.append('\t').append(k).append('=').append(v));
		content.append(nl).append(smallLine).append(nl);
		content.append(markdownDocument.body());
		this.log.info(content.toString());
	}

	private void ingest(long mogulId, long blogId, ArchiveFile archiveFile) {
		this.log.info("ingesting {}/{} for {}", mogulId, blogId, archiveFile);
		var contents = new String(archiveFile.content());
		// var dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormat);
		var markdownDocument = this.markdownDocuments.parse(contents);

		if (this.log.isDebugEnabled())
			this.debug(markdownDocument);

		var published = markdownDocument.header().publishedAt();
		var post = this.blogService.createPost( //
				blogId, //
				published, //
				this.trim(markdownDocument.header().title()), //
				this.trim(markdownDocument.body()), //
				null //
		);
		Assert.notNull(post, "post could not be created");
	}

	private String trim(String input) {
		return input == null ? "" : input.trim();
	}

}
